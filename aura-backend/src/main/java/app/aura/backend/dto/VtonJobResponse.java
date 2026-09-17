package app.aura.backend.dto;

import app.aura.backend.model.VtonJob;
import app.aura.backend.model.VtonJobStatus;
import java.time.Instant;

/**
 * VTON is durumu cevabi.
 *
 * @param resultImageUri    worker tarafindaki ham URI (debug / ic kullanim)
 * @param resultImageUrl    Java guvenli proxy — istemci bunu kullanir
 * @param resultImageBase64 optimize onizleme (optimistic render)
 * @param lookbookSaved     Lookbook arsivine eklendi mi
 */
public record VtonJobResponse(
        Long jobId,
        Long userId,
        Long wardrobeItemId,
        VtonJobStatus status,
        String workerJobId,
        String resultImageUri,
        String resultImageUrl,
        String resultImageBase64,
        String errorMessage,
        boolean lookbookSaved,
        Instant createdAt,
        Instant updatedAt) {

    public static VtonJobResponse from(VtonJob job) {
        return new VtonJobResponse(
                job.getId(),
                job.getUser().getId(),
                job.getWardrobeItemId(),
                job.getStatus(),
                job.getWorkerJobId(),
                job.getResultImageUri(),
                proxyUrlFor(job),
                job.getResultImageBase64(),
                job.getErrorMessage(),
                job.isLookbookSaved(),
                job.getCreatedAt(),
                job.getUpdatedAt());
    }

    /** Tamamlanmis isler icin istemciye acik proxy yolu. */
    public static String proxyUrlFor(VtonJob job) {
        if (job.getStatus() != VtonJobStatus.COMPLETED || job.getId() == null) {
            return null;
        }
        return "/api/v1/aura/vton/results/" + job.getId() + "/image";
    }
}
