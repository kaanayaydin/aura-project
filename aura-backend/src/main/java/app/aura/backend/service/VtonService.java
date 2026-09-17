package app.aura.backend.service;

import app.aura.backend.config.VtonProperties;
import app.aura.backend.dto.VtonJobResponse;
import app.aura.backend.dto.VtonLookbookEntryResponse;
import app.aura.backend.dto.VtonRequest;
import app.aura.backend.engine.ClothTypeMapper;
import app.aura.backend.model.User;
import app.aura.backend.model.VtonJob;
import app.aura.backend.model.VtonJobStatus;
import app.aura.backend.model.WardrobeItem;
import app.aura.backend.repository.UserRepository;
import app.aura.backend.repository.VtonJobRepository;
import app.aura.backend.repository.WardrobeItemRepository;
import app.aura.backend.service.VtonWorkerClient.WorkerStatusSnapshot;
import app.aura.backend.web.UserNotFoundException;
import app.aura.backend.web.VtonJobNotFoundException;
import app.aura.backend.web.VtonOwnershipException;
import app.aura.backend.web.VtonWorkerUnavailableException;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

/**
 * Virtual Try-On orkestrasyonu.
 *
 * 1) Dolap sahiplik guardrail + gunluk kota
 * 2) Job kaydi + Python/RunPod worker enqueue
 * 3) Status poll'da worker durumunu senkronize et; FAILED'da kota iadesi
 */
@Service
public class VtonService {

    private static final Logger log = LoggerFactory.getLogger(VtonService.class);

    private final VtonJobRepository vtonJobRepository;
    private final WardrobeItemRepository wardrobeItemRepository;
    private final UserRepository userRepository;
    private final VtonProperties vtonProperties;
    private final WardrobeGuardrailService wardrobeGuardrailService;
    private final VtonWorkerClient vtonWorkerClient;
    private final VtonQuotaService vtonQuotaService;
    private final RestClient workerRestClient;

    public VtonService(
            VtonJobRepository vtonJobRepository,
            WardrobeItemRepository wardrobeItemRepository,
            UserRepository userRepository,
            VtonProperties vtonProperties,
            WardrobeGuardrailService wardrobeGuardrailService,
            VtonWorkerClient vtonWorkerClient,
            VtonQuotaService vtonQuotaService) {
        this.vtonJobRepository = vtonJobRepository;
        this.wardrobeItemRepository = wardrobeItemRepository;
        this.userRepository = userRepository;
        this.vtonProperties = vtonProperties;
        this.wardrobeGuardrailService = wardrobeGuardrailService;
        this.vtonWorkerClient = vtonWorkerClient;
        this.vtonQuotaService = vtonQuotaService;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(vtonProperties.connectTimeoutSeconds()));
        factory.setReadTimeout(Duration.ofSeconds(vtonProperties.readTimeoutSeconds()));
        this.workerRestClient = RestClient.builder()
                .baseUrl(vtonProperties.workerBaseUrl())
                .requestFactory(factory)
                .build();
    }

    @Transactional(noRollbackFor = VtonWorkerUnavailableException.class)
    public VtonJobResponse request(Long authenticatedUserId, VtonRequest request) {
        User user = userRepository.findById(authenticatedUserId)
                .orElseThrow(() -> new UserNotFoundException(
                        "Kullanici bulunamadi: %d".formatted(authenticatedUserId)));
        WardrobeItem item = wardrobeGuardrailService.requireOwnedItem(
                user.getId(), request.wardrobeItemId());

        // Kota: kuyruga almadan once tuket; worker hatasinda iade
        vtonQuotaService.consume(user);

        VtonJob job = new VtonJob(
                user,
                item.getId(),
                request.personImageBase64(),
                request.personImageUrl());
        job.markQuotaCharged();
        job = vtonJobRepository.save(job);

        String garmentImageUrl = item.getImageUrl();
        String garmentImageBase64 = item.getImageBase64();
        String clothType = ClothTypeMapper.fromCategory(item.getCategory());
        try {
            String workerJobId = vtonWorkerClient.enqueue(new VtonWorkerClient.EnqueuePayload(
                    job.getId(),
                    user.getId(),
                    item.getId(),
                    request.personImageBase64(),
                    garmentImageBase64,
                    request.personImageUrl(),
                    garmentImageUrl,
                    clothType));
            job.markQueued(workerJobId);
            job = vtonJobRepository.save(job);
        } catch (VtonWorkerUnavailableException exception) {
            job.markFailed(exception.getMessage());
            refundQuota(job, user);
            vtonJobRepository.save(job);
            throw exception;
        } catch (RuntimeException exception) {
            job.markFailed(exception.getMessage());
            refundQuota(job, user);
            vtonJobRepository.save(job);
            throw new VtonWorkerUnavailableException(
                    "VTON worker erisilemiyor: " + exception.getMessage(), exception);
        }

        log.info(
                "VTON istek alindi: jobId={} workerJobId={} userId={} itemId={} category={} clothType={} status={} garmentUrl={} dailyCount={}",
                job.getId(),
                job.getWorkerJobId(),
                user.getId(),
                item.getId(),
                item.getCategory(),
                clothType,
                job.getStatus(),
                garmentImageUrl,
                user.getDailyVtonCount());

        return VtonJobResponse.from(job);
    }

    @Transactional
    public VtonJobResponse status(Long jobId, Long authenticatedUserId) {
        VtonJob job = requireOwnedJob(jobId, authenticatedUserId);

        if (vtonProperties.mockAutoAdvanceOnPoll()) {
            advanceMockPipeline(job);
            job = vtonJobRepository.save(job);
        } else {
            syncFromWorker(job);
            job = vtonJobRepository.save(job);
        }

        return VtonJobResponse.from(job);
    }

    /**
     * Lookbook arsivi — kullanicinin kaydettigi COMPLETED VTON kayitlari.
     */
    @Transactional(readOnly = true)
    public List<VtonLookbookEntryResponse> lookbook(Long authenticatedUserId) {
        List<VtonJob> jobs = vtonJobRepository
                .findByUserIdAndStatusAndLookbookSavedTrueOrderByCreatedAtDesc(
                        authenticatedUserId, VtonJobStatus.COMPLETED);
        return toLookbookEntries(authenticatedUserId, jobs);
    }

    /**
     * Tamamlanmis VTON'u Lookbook'a ekler (idempotent).
     */
    @Transactional
    public VtonLookbookEntryResponse saveToLookbook(Long jobId, Long authenticatedUserId) {
        VtonJob job = requireOwnedJob(jobId, authenticatedUserId);

        if (job.getStatus() != VtonJobStatus.COMPLETED) {
            throw new VtonJobNotFoundException(
                    "Yalnizca tamamlanmis VTON Lookbook'a eklenebilir: " + jobId);
        }

        if (!job.isLookbookSaved()) {
            job.markLookbookSaved();
            job = vtonJobRepository.save(job);
            log.info("VTON Lookbook'a eklendi: jobId={} userId={}", jobId, authenticatedUserId);
        }

        WardrobeItem item = wardrobeItemRepository
                .findByIdAndUserId(job.getWardrobeItemId(), authenticatedUserId)
                .orElse(null);
        return VtonLookbookEntryResponse.from(job, item);
    }

    /**
     * Worker ciktisini istemciye proxy eder; JWT sahiplik zorunlu.
     */
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> proxyResultImage(Long jobId, Long authenticatedUserId) {
        VtonJob job = requireOwnedJob(jobId, authenticatedUserId);
        if (job.getStatus() != VtonJobStatus.COMPLETED) {
            throw new VtonJobNotFoundException("VTON sonucu henuz hazir degil: " + jobId);
        }

        byte[] bytes = resolveResultBytes(job);
        MediaType mediaType = detectImageMediaType(bytes);
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header("Cache-Control", "private, max-age=3600")
                .body(bytes);
    }

    private byte[] resolveResultBytes(VtonJob job) {
        // 1) DB'deki onizleme / mock base64
        if (job.getResultImageBase64() != null && !job.getResultImageBase64().isBlank()) {
            try {
                return Base64.getDecoder().decode(job.getResultImageBase64().replaceAll("\\s", ""));
            } catch (IllegalArgumentException ignored) {
                log.warn("VTON resultImageBase64 cozulemedi jobId={}", job.getId());
            }
        }

        String uri = job.getResultImageUri();
        if (uri == null || uri.isBlank()) {
            throw new VtonJobNotFoundException("VTON sonuc gorseli yok: " + job.getId());
        }

        // 2) Worker HTTP URI (ornek: http://127.0.0.1:8001/outputs/42.png)
        if (uri.startsWith("http://") || uri.startsWith("https://")) {
            try {
                byte[] remote = RestClient.create()
                        .get()
                        .uri(uri)
                        .retrieve()
                        .body(byte[].class);
                if (remote != null && remote.length > 0) {
                    return remote;
                }
            } catch (Exception exception) {
                log.warn("VTON worker gorseli alinamadi uri={}: {}", uri, exception.getMessage());
            }
        }

        // 3) Yerel worker outputs yolu (jobId.png)
        try {
            byte[] fromWorker = workerRestClient.get()
                    .uri("/outputs/{file}", job.getId() + ".png")
                    .retrieve()
                    .body(byte[].class);
            if (fromWorker != null && fromWorker.length > 0) {
                return fromWorker;
            }
        } catch (Exception exception) {
            log.warn(
                    "VTON /outputs proxy basarisiz jobId={}: {}",
                    job.getId(),
                    exception.getMessage());
        }

        throw new VtonJobNotFoundException("VTON sonuc gorseli alinamadi: " + job.getId());
    }

    private List<VtonLookbookEntryResponse> toLookbookEntries(Long userId, List<VtonJob> jobs) {
        if (jobs.isEmpty()) {
            return List.of();
        }
        Map<Long, WardrobeItem> itemsById = wardrobeItemRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(WardrobeItem::getId, Function.identity(), (a, b) -> a));
        return jobs.stream()
                .map(job -> VtonLookbookEntryResponse.from(
                        job, itemsById.get(job.getWardrobeItemId())))
                .filter(Objects::nonNull)
                .toList();
    }

    private static MediaType detectImageMediaType(byte[] bytes) {
        if (bytes != null && bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF) {
            return MediaType.IMAGE_JPEG;
        }
        return MediaType.IMAGE_PNG;
    }

    private void syncFromWorker(VtonJob job) {
        if (job.getWorkerJobId() == null || job.getWorkerJobId().isBlank()) {
            return;
        }
        if (job.getStatus() == VtonJobStatus.COMPLETED || job.getStatus() == VtonJobStatus.FAILED) {
            return;
        }
        try {
            WorkerStatusSnapshot snapshot = vtonWorkerClient.status(job.getWorkerJobId());
            applyWorkerSnapshot(job, snapshot);
        } catch (Exception exception) {
            log.warn(
                    "VTON worker status alinamadi (jobId={}, workerJobId={}): {}",
                    job.getId(),
                    job.getWorkerJobId(),
                    exception.getMessage());
        }
    }

    private void applyWorkerSnapshot(VtonJob job, WorkerStatusSnapshot snapshot) {
        if (snapshot == null || snapshot.status() == null) {
            return;
        }
        String raw = snapshot.status().trim().toUpperCase();
        switch (raw) {
            case "QUEUED", "PENDING", "IN_QUEUE" -> job.markQueued(job.getWorkerJobId());
            case "PROCESSING", "STARTED", "IN_PROGRESS", "RUNNING" -> job.markProcessing();
            case "COMPLETED", "SUCCESS" -> job.markCompleted(
                    snapshot.resultImageUri() != null
                            ? snapshot.resultImageUri()
                            : "mock://catvton/result/" + job.getId(),
                    snapshot.resultImageBase64());
            case "FAILED", "FAILURE" -> {
                job.markFailed(
                        snapshot.errorMessage() != null
                                ? snapshot.errorMessage()
                                : "VTON worker basarisiz");
                refundQuota(job, job.getUser());
            }
            default -> log.debug("Bilinmeyen VTON worker status: {}", raw);
        }
    }

    private void refundQuota(VtonJob job, User user) {
        if (job == null || user == null) {
            return;
        }
        if (!job.isQuotaCharged() || job.isQuotaRefunded()) {
            return;
        }
        vtonQuotaService.refund(user);
        job.markQuotaRefunded();
    }

    private void advanceMockPipeline(VtonJob job) {
        if (job.getStatus() == VtonJobStatus.QUEUED) {
            job.markProcessing();
            log.debug("Mock VTON ilerleme: jobId={} → PROCESSING", job.getId());
        } else if (job.getStatus() == VtonJobStatus.PROCESSING) {
            // 1x1 PNG — Flutter Image.memory ile dogrudan gosterilebilir
            String mockPng = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";
            job.markCompleted("mock://vton/result/" + job.getId(), mockPng);
            log.debug("Mock VTON ilerleme: jobId={} → COMPLETED", job.getId());
        }
    }

    private VtonJob requireOwnedJob(Long jobId, Long authenticatedUserId) {
        VtonJob job = vtonJobRepository.findById(jobId)
                .orElseThrow(() -> new VtonJobNotFoundException("VTON isi bulunamadi: " + jobId));
        if (!job.getUser().getId().equals(authenticatedUserId)) {
            throw new VtonOwnershipException(
                    "Bu VTON kaydina erisim yetkiniz yok: jobId=" + jobId);
        }
        return job;
    }
}
