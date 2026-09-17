package app.aura.backend.web;

import app.aura.backend.dto.AddUserPerfumeRequest;
import app.aura.backend.dto.UserPerfumeResponse;
import app.aura.backend.security.SecurityUtils;
import app.aura.backend.service.UserPerfumeService;
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
 * Kullanici parfum rafi HTTP arayuzu — kimlik JWT Principal'dan.
 */
@RestController
@RequestMapping("/api/v1/user/perfumes")
public class UserPerfumeController {

    private final UserPerfumeService userPerfumeService;

    public UserPerfumeController(UserPerfumeService userPerfumeService) {
        this.userPerfumeService = userPerfumeService;
    }

    /** Kuratorlu katalog; {@code onShelf} rafta olup olmadigini isaretler. */
    @GetMapping("/catalog")
    public List<UserPerfumeResponse> catalog() {
        return userPerfumeService.listCatalog(SecurityUtils.requireUserId());
    }

    /** Kullanicinin favori rafi. */
    @GetMapping
    public List<UserPerfumeResponse> list() {
        return userPerfumeService.listShelf(SecurityUtils.requireUserId());
    }

    /** Katalogdan rafa ekle. */
    @PostMapping
    public ResponseEntity<UserPerfumeResponse> add(@Valid @RequestBody AddUserPerfumeRequest request) {
        UserPerfumeResponse response = userPerfumeService.addFromCatalog(
                SecurityUtils.requireUserId(), request);
        return ResponseEntity
                .created(URI.create("/api/v1/user/perfumes/" + response.id()))
                .body(response);
    }

    /** Raftan cikar. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remove(@PathVariable Long id) {
        userPerfumeService.remove(id, SecurityUtils.requireUserId());
        return ResponseEntity.noContent().build();
    }
}
