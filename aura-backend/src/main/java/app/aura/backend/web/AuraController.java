package app.aura.backend.web;

import app.aura.backend.dto.AuraSuggestionResponse;
import app.aura.backend.dto.SuggestAuraRequest;
import app.aura.backend.security.SecurityUtils;
import app.aura.backend.service.AuraService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Baglam ve Termodinamik Motoru HTTP arayuzu — kimlik JWT Principal'dan.
 */
@RestController
@RequestMapping("/api/v1/aura")
public class AuraController {

    private final AuraService auraService;

    public AuraController(AuraService auraService) {
        this.auraService = auraService;
    }

    /**
     * Hava + takvim baglamina gore dolaptan kombin onerir.
     */
    @PostMapping("/suggest")
    public AuraSuggestionResponse suggest(@Valid @RequestBody SuggestAuraRequest request) {
        return auraService.suggest(SecurityUtils.requireUserId(), request);
    }
}
