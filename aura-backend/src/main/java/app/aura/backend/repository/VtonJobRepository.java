package app.aura.backend.repository;

import app.aura.backend.model.VtonJob;
import app.aura.backend.model.VtonJobStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VtonJobRepository extends JpaRepository<VtonJob, Long> {

    Optional<VtonJob> findByIdAndUserId(Long id, Long userId);

    List<VtonJob> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<VtonJob> findByUserIdAndStatusAndLookbookSavedTrueOrderByCreatedAtDesc(
            Long userId, VtonJobStatus status);
}
