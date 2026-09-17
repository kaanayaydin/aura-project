package app.aura.backend.service;

import app.aura.backend.dto.FavoriteOutfitResponse;
import app.aura.backend.dto.SaveFavoriteRequest;
import app.aura.backend.model.OutfitFavorite;
import app.aura.backend.model.User;
import app.aura.backend.repository.OutfitFavoriteRepository;
import app.aura.backend.repository.UserRepository;
import app.aura.backend.web.FavoriteNotFoundException;
import app.aura.backend.web.FavoriteOwnershipException;
import app.aura.backend.web.UserNotFoundException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Favori kombin kayitlari — kimlik JWT authenticatedUserId ile gelir.
 */
@Service
public class FavoriteOutfitService {

    private static final Logger log = LoggerFactory.getLogger(FavoriteOutfitService.class);

    private final OutfitFavoriteRepository favoriteRepository;
    private final UserRepository userRepository;

    public FavoriteOutfitService(
            OutfitFavoriteRepository favoriteRepository,
            UserRepository userRepository) {
        this.favoriteRepository = favoriteRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public FavoriteOutfitResponse save(Long authenticatedUserId, SaveFavoriteRequest request) {
        User user = userRepository.findById(authenticatedUserId)
                .orElseThrow(() -> new UserNotFoundException(
                        "Kullanici bulunamadi: %d".formatted(authenticatedUserId)));
        OutfitFavorite favorite = new OutfitFavorite(
                request.vibe(),
                request.summary(),
                request.occasion(),
                request.temperatureCelsius(),
                request.seasonBand(),
                request.matchScore(),
                request.colorHarmonyType(),
                request.colorHarmonyScore(),
                request.topItemId(),
                request.bottomItemId(),
                request.accessoryItemId(),
                request.perfumeCatalogId(),
                request.perfumeLabel());
        user.addFavorite(favorite);
        OutfitFavorite saved = favoriteRepository.save(favorite);
        log.info("Favori kombin kaydedildi: id={} kullanici={} vibe={}",
                saved.getId(), user.getId(), saved.getVibe());
        return FavoriteOutfitResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<FavoriteOutfitResponse> list(Long authenticatedUserId) {
        return favoriteRepository.findByUserIdOrderByCreatedAtDesc(authenticatedUserId).stream()
                .map(FavoriteOutfitResponse::from)
                .toList();
    }

    @Transactional
    public void delete(Long favoriteId, Long authenticatedUserId) {
        OutfitFavorite favorite = favoriteRepository.findById(favoriteId)
                .orElseThrow(() -> new FavoriteNotFoundException(
                        "Favori bulunamadi: " + favoriteId));
        if (!favorite.getUser().getId().equals(authenticatedUserId)) {
            throw new FavoriteOwnershipException(
                    "Bu favoriye erisim yetkiniz yok: id=" + favoriteId);
        }
        User user = favorite.getUser();
        user.removeFavorite(favorite);
        favoriteRepository.delete(favorite);
        log.info("Favori silindi: id={} kullanici={}", favoriteId, authenticatedUserId);
    }
}
