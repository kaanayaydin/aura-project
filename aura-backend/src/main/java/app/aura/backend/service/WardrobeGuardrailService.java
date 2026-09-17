package app.aura.backend.service;

import app.aura.backend.model.WardrobeItem;
import app.aura.backend.repository.WardrobeItemRepository;
import app.aura.backend.web.VtonOwnershipException;
import app.aura.backend.web.WardrobeItemNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dolap envanter sahiplik guardrail'i.
 *
 * Chat tarafindaki metin filtresi {@code WardrobeGuardrail}'den ayridir;
 * VTON ve benzeri isteklerde wardrobeItemId'nin kullaniciya ait oldugunu dogrular.
 */
@Service
public class WardrobeGuardrailService {

    private final WardrobeItemRepository wardrobeItemRepository;

    public WardrobeGuardrailService(WardrobeItemRepository wardrobeItemRepository) {
        this.wardrobeItemRepository = wardrobeItemRepository;
    }

    /**
     * Parcanin var oldugunu ve {@code userId} sahibine ait oldugunu zorunlu kılar.
     *
     * @throws WardrobeItemNotFoundException parca yoksa (404)
     * @throws VtonOwnershipException        baska kullaniciya aitse (403)
     */
    @Transactional(readOnly = true)
    public WardrobeItem requireOwnedItem(Long userId, Long wardrobeItemId) {
        if (wardrobeItemId == null) {
            throw new WardrobeItemNotFoundException("wardrobeItemId zorunludur.");
        }
        WardrobeItem item = wardrobeItemRepository.findById(wardrobeItemId)
                .orElseThrow(() -> new WardrobeItemNotFoundException(
                        "Dolap parcasi bulunamadi: " + wardrobeItemId));

        Long ownerId = item.getUser().getId();
        if (userId == null || !ownerId.equals(userId)) {
            throw new VtonOwnershipException(
                    "Dolap parcasi %d kullanici %d envanterinde degil."
                            .formatted(wardrobeItemId, userId));
        }
        return item;
    }
}
