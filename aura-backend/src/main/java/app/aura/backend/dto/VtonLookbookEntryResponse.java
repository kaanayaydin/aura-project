package app.aura.backend.dto;

import app.aura.backend.model.VtonJob;
import app.aura.backend.model.VtonJobStatus;
import app.aura.backend.model.WardrobeItem;
import java.time.Instant;

/**
 * Lookbook / gecmis listesi satiri — tamamlanmis ve arsive eklenmis VTON.
 */
public record VtonLookbookEntryResponse(
        Long jobId,
        Long userId,
        Long wardrobeItemId,
        String category,
        String color,
        String resultImageUrl,
        String resultImageBase64,
        Instant createdAt,
        boolean lookbookSaved) {

    public static VtonLookbookEntryResponse from(VtonJob job, WardrobeItem item) {
        return new VtonLookbookEntryResponse(
                job.getId(),
                job.getUser().getId(),
                job.getWardrobeItemId(),
                item != null ? item.getCategory() : null,
                item != null ? item.getColor() : null,
                job.getStatus() == VtonJobStatus.COMPLETED
                        ? VtonJobResponse.proxyUrlFor(job)
                        : null,
                job.getResultImageBase64(),
                job.getCreatedAt(),
                job.isLookbookSaved());
    }
}
