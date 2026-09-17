package app.aura.backend.web;

import app.aura.backend.dto.FavoriteOutfitResponse;
import app.aura.backend.dto.SaveFavoriteRequest;
import app.aura.backend.security.SecurityUtils;
import app.aura.backend.service.FavoriteOutfitService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Favori kombin HTTP arayuzu — kimlik JWT Principal'dan.
 */
@RestController
@RequestMapping("/api/v1/aura/favorites")
public class FavoriteOutfitController {

    private final FavoriteOutfitService favoriteOutfitService;

    public FavoriteOutfitController(FavoriteOutfitService favoriteOutfitService) {
        this.favoriteOutfitService = favoriteOutfitService;
    }

    @PostMapping
    public ResponseEntity<FavoriteOutfitResponse> save(@Valid @RequestBody SaveFavoriteRequest request) {
        Long userId = SecurityUtils.requireUserId();
        FavoriteOutfitResponse response = favoriteOutfitService.save(userId, request);
        return ResponseEntity
                .created(URI.create("/api/v1/aura/favorites/" + response.id()))
                .body(response);
    }

    @GetMapping
    public List<FavoriteOutfitResponse> list() {
        return favoriteOutfitService.list(SecurityUtils.requireUserId());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        favoriteOutfitService.delete(id, SecurityUtils.requireUserId());
        return ResponseEntity.noContent().build();
    }
}
