package app.aura.backend.web;

import app.aura.backend.dto.ChatAuraRequest;
import app.aura.backend.dto.ChatAuraResponse;
import app.aura.backend.security.SecurityUtils;
import app.aura.backend.service.AuraChatService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lokal LLM destekli Aura AI sohbet HTTP arayuzu — kimlik JWT Principal'dan.
 */
@RestController
@RequestMapping("/api/v1/aura")
public class AuraChatController {

    private final AuraChatService auraChatService;

    public AuraChatController(AuraChatService auraChatService) {
        this.auraChatService = auraChatService;
    }

    /**
     * Kullanici sorusunu dolap / raf / hava baglamiyla Ollama'ya iletir.
     * Guardrail yalnizca authenticated kullanicinin dolap + raf verisini kullanir.
     */
    @PostMapping("/chat")
    public ChatAuraResponse chat(@Valid @RequestBody ChatAuraRequest request) {
        return auraChatService.chat(SecurityUtils.requireUserId(), request);
    }
}
