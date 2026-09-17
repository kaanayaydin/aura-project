package app.aura.backend.web;

import app.aura.backend.dto.PresignUploadRequest;
import app.aura.backend.dto.PresignUploadResponse;
import app.aura.backend.security.SecurityUtils;
import app.aura.backend.service.StorageService;
import app.aura.backend.service.StorageService.Purpose;
import app.aura.backend.service.storage.ObjectStorage.PresignedUpload;
import jakarta.validation.Valid;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * POST /api/v1/storage/upload-url — istemci dogrudan MinIO/R2'ye PUT eder.
 */
@RestController
@RequestMapping("/api/v1/storage")
public class StorageController {

    private final StorageService storageService;

    public StorageController(StorageService storageService) {
        this.storageService = storageService;
    }

    @PostMapping("/upload-url")
    @ResponseStatus(HttpStatus.OK)
    public PresignUploadResponse uploadUrl(@Valid @RequestBody PresignUploadRequest request) {
        SecurityUtils.requireUserId();
        Purpose purpose = parsePurpose(request.purpose());
        try {
            PresignedUpload upload = storageService.generatePresignedUploadUrl(
                    purpose, request.contentType(), request.filename());
            return new PresignUploadResponse(
                    upload.uploadUrl(),
                    upload.objectUrl(),
                    upload.bucket(),
                    upload.key(),
                    upload.ttl().toSeconds());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    private static Purpose parsePurpose(String raw) {
        String value = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (value) {
            case "WARDROBE", "WARDROBE_ITEM" -> Purpose.WARDROBE;
            case "VTON_PERSON", "PERSON", "AVATAR_PERSON" -> Purpose.VTON_PERSON;
            case "VTON_RESULT", "RESULT" -> Purpose.VTON_RESULT;
            case "AVATAR" -> Purpose.AVATAR;
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Gecersiz purpose: " + raw);
        };
    }
}
