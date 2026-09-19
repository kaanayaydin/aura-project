package app.aura.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.aura.backend.config.AuraProperties;
import app.aura.backend.dto.CreateWardrobeItemRequest;
import app.aura.backend.dto.WardrobeItemResponse;
import app.aura.backend.model.User;
import app.aura.backend.model.WardrobeItem;
import app.aura.backend.repository.UserRepository;
import app.aura.backend.repository.WardrobeItemRepository;
import app.aura.backend.service.StorageService.Purpose;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WardrobeServiceAlreadyNormalizedTest {

    private static final byte[] SOURCE_PNG = {
        (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x01, 0x02, 0x03
    };
    private static final byte[] STUDIO_PNG = {
        (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x09, 0x09, 0x09, 0x09
    };
    private static final String SOURCE_B64 = Base64.getEncoder().encodeToString(SOURCE_PNG);

    @Mock
    private WardrobeItemRepository wardrobeItemRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private StorageService storageService;

    @Mock
    private VisionGarmentClient visionGarmentClient;

    private WardrobeService wardrobeService;

    @BeforeEach
    void setUp() {
        wardrobeService = new WardrobeService(
                wardrobeItemRepository,
                userRepository,
                new AuraProperties("demo", 10 * 1024 * 1024),
                storageService,
                visionGarmentClient);
    }

    @Test
    void alreadyNormalizedTrue_callsNormalizeWithSkipOrientation() {
        User user = userWithId(7L);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(visionGarmentClient.normalizeGarmentPng(any(), anyString(), eq(true)))
                .thenReturn(Optional.of(STUDIO_PNG));
        when(storageService.uploadBytes(eq(Purpose.WARDROBE), any(), anyString(), anyString()))
                .thenReturn("http://127.0.0.1:9000/aura-wardrobe/framed.png");
        when(wardrobeItemRepository.save(any(WardrobeItem.class))).thenAnswer(invocation -> {
            WardrobeItem item = invocation.getArgument(0);
            setId(item, 42L);
            return item;
        });

        CreateWardrobeItemRequest request = new CreateWardrobeItemRequest(
                null, "t-shirt", 0.8, SOURCE_B64, null, null, true);

        WardrobeItemResponse response = wardrobeService.createItem(7L, request);

        verify(visionGarmentClient).normalizeGarmentPng(eq(SOURCE_PNG), eq("t-shirt.png"), eq(true));
        ArgumentCaptor<byte[]> uploaded = ArgumentCaptor.forClass(byte[].class);
        verify(storageService).uploadBytes(eq(Purpose.WARDROBE), uploaded.capture(), anyString(), eq("t-shirt"));
        assertThat(uploaded.getValue()).isEqualTo(STUDIO_PNG);
        assertThat(response.imageBytes()).isEqualTo(STUDIO_PNG.length);
        assertThat(response.imageUrl()).contains("framed.png");
    }

    @Test
    void alreadyNormalizedFalse_stillCallsNormalizeGarmentPng() {
        User user = userWithId(8L);
        when(userRepository.findById(8L)).thenReturn(Optional.of(user));
        when(visionGarmentClient.normalizeGarmentPng(any(), anyString(), eq(false)))
                .thenReturn(Optional.of(STUDIO_PNG));
        when(storageService.uploadBytes(eq(Purpose.WARDROBE), any(), anyString(), anyString()))
                .thenReturn("http://127.0.0.1:9000/aura-wardrobe/studio.png");
        when(wardrobeItemRepository.save(any(WardrobeItem.class))).thenAnswer(invocation -> {
            WardrobeItem item = invocation.getArgument(0);
            setId(item, 43L);
            return item;
        });

        CreateWardrobeItemRequest request = new CreateWardrobeItemRequest(
                null, "shirt", 0.5, SOURCE_B64, null, null, false);

        wardrobeService.createItem(8L, request);

        verify(visionGarmentClient).normalizeGarmentPng(eq(SOURCE_PNG), eq("shirt.png"), eq(false));
        ArgumentCaptor<byte[]> uploaded = ArgumentCaptor.forClass(byte[].class);
        verify(storageService).uploadBytes(eq(Purpose.WARDROBE), uploaded.capture(), anyString(), eq("shirt"));
        assertThat(uploaded.getValue()).isEqualTo(STUDIO_PNG);
    }

    @Test
    void missingAlreadyNormalizedJson_defaultsFalseAndStillNormalizes() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        CreateWardrobeItemRequest request = mapper.readValue(
                """
                {"category":"jacket","imageBase64":"%s"}
                """
                        .formatted(SOURCE_B64),
                CreateWardrobeItemRequest.class);
        assertThat(request.alreadyNormalized()).isFalse();

        User user = userWithId(9L);
        when(userRepository.findById(9L)).thenReturn(Optional.of(user));
        when(visionGarmentClient.normalizeGarmentPng(any(), anyString(), anyBoolean()))
                .thenReturn(Optional.of(STUDIO_PNG));
        when(storageService.uploadBytes(eq(Purpose.WARDROBE), any(), anyString(), anyString()))
                .thenReturn("http://127.0.0.1:9000/aura-wardrobe/legacy.png");
        when(wardrobeItemRepository.save(any(WardrobeItem.class))).thenAnswer(invocation -> {
            WardrobeItem item = invocation.getArgument(0);
            setId(item, 44L);
            return item;
        });

        wardrobeService.createItem(9L, request);

        verify(visionGarmentClient).normalizeGarmentPng(any(), any(), eq(false));
        ArgumentCaptor<byte[]> uploaded = ArgumentCaptor.forClass(byte[].class);
        verify(storageService).uploadBytes(eq(Purpose.WARDROBE), uploaded.capture(), anyString(), eq("jacket"));
        assertThat(uploaded.getValue()).isEqualTo(STUDIO_PNG);
    }

    private static User userWithId(Long id) {
        User user = new User("ward-user-" + id, "ward-" + id + "@aura.app");
        setId(user, id);
        return user;
    }

    private static void setId(Object entity, Long id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
