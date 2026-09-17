package app.aura.backend.dto;

/**
 * Aura AI sohbet cevabi.
 *
 * @param reply          asistan yaniti
 * @param model          kullanilan model adi
 * @param source         ollama | fallback
 * @param wardrobeCount  prompt'a enjekte edilen dolap parca sayisi
 * @param perfumeCount   raf sise sayisi
 * @param weatherSummary kisa hava ozeti
 */
public record ChatAuraResponse(
        String reply,
        String model,
        String source,
        int wardrobeCount,
        int perfumeCount,
        String weatherSummary) {
}
