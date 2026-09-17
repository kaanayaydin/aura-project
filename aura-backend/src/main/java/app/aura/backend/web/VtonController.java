package app.aura.backend.web;

import app.aura.backend.dto.VtonJobResponse;
import app.aura.backend.dto.VtonLookbookEntryResponse;
import app.aura.backend.dto.VtonRequest;
import app.aura.backend.security.SecurityUtils;
import app.aura.backend.service.VtonService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Virtual Try-On HTTP arayuzu — kimlik JWT Principal'dan.
 */
@RestController
@RequestMapping("/api/v1/aura/vton")
public class VtonController {

    private final VtonService vtonService;

    public VtonController(VtonService vtonService) {
        this.vtonService = vtonService;
    }

    @PostMapping("/request")
    public ResponseEntity<VtonJobResponse> request(@Valid @RequestBody VtonRequest request) {
        Long userId = SecurityUtils.requireUserId();
        VtonJobResponse response = vtonService.request(userId, request);
        return ResponseEntity
                .created(URI.create("/api/v1/aura/vton/status/" + response.jobId()))
                .body(response);
    }

    @GetMapping("/status/{jobId}")
    public VtonJobResponse status(@PathVariable Long jobId) {
        return vtonService.status(jobId, SecurityUtils.requireUserId());
    }

    @GetMapping({"/lookbook", "/history"})
    public List<VtonLookbookEntryResponse> lookbook() {
        return vtonService.lookbook(SecurityUtils.requireUserId());
    }

    @PostMapping("/lookbook/{jobId}")
    public VtonLookbookEntryResponse saveToLookbook(@PathVariable Long jobId) {
        return vtonService.saveToLookbook(jobId, SecurityUtils.requireUserId());
    }

    /**
     * Worker ciktisini guvenli proxy ile sunar (JWT sahiplik).
     */
    @GetMapping("/results/{jobId}/image")
    public ResponseEntity<byte[]> resultImage(@PathVariable Long jobId) {
        return vtonService.proxyResultImage(jobId, SecurityUtils.requireUserId());
    }
}
