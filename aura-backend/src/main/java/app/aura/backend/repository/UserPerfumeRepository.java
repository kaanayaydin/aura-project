package app.aura.backend.repository;

import app.aura.backend.model.UserPerfume;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPerfumeRepository extends JpaRepository<UserPerfume, Long> {

    List<UserPerfume> findByUserIdOrderByBrandAscNameAsc(Long userId);

    boolean existsByUserIdAndCatalogId(Long userId, String catalogId);

    Optional<UserPerfume> findByIdAndUserId(Long id, Long userId);
}
