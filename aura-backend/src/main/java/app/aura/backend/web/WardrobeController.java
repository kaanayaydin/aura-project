package app.aura.backend.web;

import app.aura.backend.dto.CreateWardrobeItemRequest;
import app.aura.backend.dto.WardrobeItemResponse;
import app.aura.backend.security.SecurityUtils;
import app.aura.backend.service.WardrobeService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sanal dolap HTTP arayuzu — kimlik JWT Principal'dan.
 */
@RestController
@RequestMapping("/api/v1/wardrobe")
public class WardrobeController {

    private final WardrobeService wardrobeService;

    public WardrobeController(WardrobeService wardrobeService) {
        this.wardrobeService = wardrobeService;
    }

    @PostMapping("/items")
    public ResponseEntity<WardrobeItemResponse> createItem(
            @Valid @RequestBody CreateWardrobeItemRequest request) {
        Long userId = SecurityUtils.requireUserId();
        WardrobeItemResponse response = wardrobeService.createItem(userId, request);
        return ResponseEntity
                .created(URI.create("/api/v1/wardrobe/items/" + response.id()))
                .body(response);
    }

    @GetMapping("/items")
    public List<WardrobeItemResponse> listItems(
            @RequestParam(defaultValue = "false") boolean includeImages) {
        return wardrobeService.listItems(SecurityUtils.requireUserId(), includeImages);
    }

    @GetMapping("/items/{id}")
    public WardrobeItemResponse getItem(@PathVariable Long id) {
        return wardrobeService.getItem(SecurityUtils.requireUserId(), id);
    }
}
