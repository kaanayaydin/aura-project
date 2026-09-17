package app.aura.backend.repository;

import app.aura.backend.model.OutfitFavorite;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutfitFavoriteRepository extends JpaRepository<OutfitFavorite, Long> {

    List<OutfitFavorite> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<OutfitFavorite> findByIdAndUserId(Long id, Long userId);
}
