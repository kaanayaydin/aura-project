package app.aura.backend.service;

import app.aura.backend.dto.AddUserPerfumeRequest;
import app.aura.backend.dto.UserPerfumeResponse;
import app.aura.backend.engine.PerfumeCatalog;
import app.aura.backend.engine.PerfumeCatalog.NichePerfume;
import app.aura.backend.model.User;
import app.aura.backend.model.UserPerfume;
import app.aura.backend.repository.UserPerfumeRepository;
import app.aura.backend.repository.UserRepository;
import app.aura.backend.web.PerfumeOwnershipException;
import app.aura.backend.web.PerfumeShelfException;
import app.aura.backend.web.UserNotFoundException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kullanici parfum rafi — kimlik JWT authenticatedUserId ile gelir.
 */
@Service
public class UserPerfumeService {

    private static final Logger log = LoggerFactory.getLogger(UserPerfumeService.class);

    private final UserPerfumeRepository userPerfumeRepository;
    private final UserRepository userRepository;

    public UserPerfumeService(
            UserPerfumeRepository userPerfumeRepository,
            UserRepository userRepository) {
        this.userPerfumeRepository = userPerfumeRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<UserPerfumeResponse> listShelf(Long authenticatedUserId) {
        requireUser(authenticatedUserId);
        return userPerfumeRepository.findByUserIdOrderByBrandAscNameAsc(authenticatedUserId).stream()
                .map(UserPerfumeResponse::fromShelf)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UserPerfumeResponse> listCatalog(Long authenticatedUserId) {
        requireUser(authenticatedUserId);
        Set<String> onShelf = userPerfumeRepository
                .findByUserIdOrderByBrandAscNameAsc(authenticatedUserId).stream()
                .map(UserPerfume::getCatalogId)
                .collect(Collectors.toSet());

        return PerfumeCatalog.all().stream()
                .map(perfume -> UserPerfumeResponse.fromCatalog(
                        perfume, onShelf.contains(perfume.id())))
                .toList();
    }

    @Transactional
    public UserPerfumeResponse addFromCatalog(Long authenticatedUserId, AddUserPerfumeRequest request) {
        NichePerfume catalogEntry = PerfumeCatalog.all().stream()
                .filter(perfume -> perfume.id().equalsIgnoreCase(request.catalogId().trim()))
                .findFirst()
                .orElseThrow(() -> new PerfumeShelfException(
                        HttpStatus.NOT_FOUND.value(),
                        "Katalogda parfum bulunamadi: " + request.catalogId()));

        User user = userRepository.findById(authenticatedUserId)
                .orElseThrow(() -> new UserNotFoundException(
                        "Kullanici bulunamadi: %d".formatted(authenticatedUserId)));
        if (userPerfumeRepository.existsByUserIdAndCatalogId(user.getId(), catalogEntry.id())) {
            throw new PerfumeShelfException(
                    HttpStatus.CONFLICT.value(),
                    "Bu parfum zaten rafta: " + catalogEntry.brand() + " " + catalogEntry.name());
        }

        String chords = String.join(", ", catalogEntry.chords());
        String notes = String.join(
                " | ",
                "ust: " + String.join(", ", catalogEntry.topNotes()),
                "kalp: " + String.join(", ", catalogEntry.heartNotes()),
                "dip: " + String.join(", ", catalogEntry.baseNotes()));

        UserPerfume perfume = new UserPerfume(
                catalogEntry.id(),
                catalogEntry.brand(),
                catalogEntry.name(),
                catalogEntry.concentration(),
                chords,
                notes,
                catalogEntry.diffusion());
        user.addPerfume(perfume);

        UserPerfume saved = userPerfumeRepository.save(perfume);
        log.info(
                "Parfum rafa eklendi: id={} kullanici={} catalog={}",
                saved.getId(), user.getId(), saved.getCatalogId());
        return UserPerfumeResponse.fromShelf(saved);
    }

    @Transactional
    public void remove(Long perfumeId, Long authenticatedUserId) {
        requireUser(authenticatedUserId);
        UserPerfume perfume = userPerfumeRepository.findById(perfumeId)
                .orElseThrow(() -> new PerfumeShelfException(
                        HttpStatus.NOT_FOUND.value(),
                        "Rafta parfum bulunamadi: " + perfumeId));
        if (!perfume.getUser().getId().equals(authenticatedUserId)) {
            throw new PerfumeOwnershipException(
                    "Bu parfum rafina erisim yetkiniz yok: id=" + perfumeId);
        }
        User user = perfume.getUser();
        user.removePerfume(perfume);
        userPerfumeRepository.delete(perfume);
        log.info("Parfum raftan cikarildi: id={} kullanici={}", perfumeId, authenticatedUserId);
    }

    private void requireUser(Long authenticatedUserId) {
        if (!userRepository.existsById(authenticatedUserId)) {
            throw new UserNotFoundException(
                    "Kullanici bulunamadi: %d".formatted(authenticatedUserId));
        }
    }
}
