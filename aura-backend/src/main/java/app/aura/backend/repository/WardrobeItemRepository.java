package app.aura.backend.repository;

import app.aura.backend.model.WardrobeItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Sanal dolap parcalarinin kalici depolama arayuzu.
 */
public interface WardrobeItemRepository extends JpaRepository<WardrobeItem, Long> {

    List<WardrobeItem> findByUserId(Long userId);

    List<WardrobeItem> findByUserIdAndCategory(Long userId, String category);

    Optional<WardrobeItem> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);
}
