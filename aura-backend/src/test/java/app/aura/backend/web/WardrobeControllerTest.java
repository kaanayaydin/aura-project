package app.aura.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.aura.backend.model.User;
import app.aura.backend.repository.UserRepository;
import app.aura.backend.repository.WardrobeItemRepository;
import app.aura.backend.security.JwtService;
import app.aura.backend.service.VisionGarmentClient;
import app.aura.backend.support.PngBombs;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * /api/v1/wardrobe — JWT kimlik (v0.17.2).
 *
 * {@code application-test.yml} {@code normalize-garment-enabled: false} koyar.
 * Gercek client no-op empty doner. Bu sinif {@link MockitoBean} ile client
 * stub'lar; alreadyNormalized=true → skipOrientation=true ile cagrilir
 * (framing_only), false/alan-yok → skipOrientation=false.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "aura.vision.normalize-garment-enabled=true")
class WardrobeControllerTest {

    private static final byte[] PNG_BYTES = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x01, 0x02, 0x03
    };
    private static final String PNG_BASE64 = Base64.getEncoder().encodeToString(PNG_BYTES);

    private static final byte[] JPEG_BYTES = {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10
    };
    private static final String JPEG_BASE64 = Base64.getEncoder().encodeToString(JPEG_BYTES);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WardrobeItemRepository wardrobeItemRepository;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private VisionGarmentClient visionGarmentClient;

    @BeforeEach
    void stubVisionClient() {
        Mockito.reset(visionGarmentClient);
        when(visionGarmentClient.normalizeGarmentPng(any(), any(), anyBoolean()))
                .thenReturn(Optional.empty());
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.issueToken(user.getId(), user.getUsername());
    }

    @Test
    void unauthenticatedCreateReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/wardrobe/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"category":"shirt","imageBase64":"%s"}
                                """.formatted(PNG_BASE64)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Kimlik dogrulamasi gerekli"));
    }

    @Test
    void unauthenticatedListReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/wardrobe/items"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createsItemForAuthenticatedUser() throws Exception {
        User user = userRepository.save(new User("kaan", "kaan@aura.app"));
        String token = bearer(user);

        String body = """
                {
                  "category": "t-shirt",
                  "categoryConfidence": 0.8734,
                  "imageBase64": "%s",
                  "color": "white"
                }
                """.formatted(PNG_BASE64);

        mockMvc.perform(post("/api/v1/wardrobe/items")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.userId").value(user.getId()))
                .andExpect(jsonPath("$.category").value("t-shirt"))
                .andExpect(jsonPath("$.categoryConfidence").value(0.8734))
                .andExpect(jsonPath("$.imageBytes").value(PNG_BYTES.length))
                .andExpect(jsonPath("$.imageMimeType").value("image/png"));

        assertThat(wardrobeItemRepository.countByUserId(user.getId())).isEqualTo(1);
    }

    @Test
    void bodyUserIdIsIgnoredInFavorOfJwtPrincipal() throws Exception {
        User owner = userRepository.save(new User("jwt-owner", "jwt-owner@aura.app"));
        User impostor = userRepository.save(new User("jwt-impostor", "jwt-impostor@aura.app"));
        String token = bearer(owner);

        mockMvc.perform(post("/api/v1/wardrobe/items")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": %d,
                                  "category": "jacket",
                                  "imageBase64": "%s"
                                }
                                """.formatted(impostor.getId(), PNG_BASE64)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(owner.getId()));

        assertThat(wardrobeItemRepository.countByUserId(owner.getId())).isEqualTo(1);
        assertThat(wardrobeItemRepository.countByUserId(impostor.getId())).isZero();
    }

    @Test
    void detectsJpegMimeType() throws Exception {
        User user = userRepository.save(new User("jpeg-user", "jpeg@aura.app"));
        mockMvc.perform(post("/api/v1/wardrobe/items")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"category":"dress","imageBase64":"%s"}
                                """.formatted(JPEG_BASE64)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imageMimeType").value("image/jpeg"));
    }

    @Test
    void normalizesDataUriPayloadOnWrite() throws Exception {
        User user = userRepository.save(new User("datauri", "datauri@aura.app"));
        String token = bearer(user);
        String body = """
                {
                  "category": "glasses",
                  "imageBase64": "data:image/png;base64,%s"
                }
                """.formatted(PNG_BASE64);

        String location = mockMvc.perform(post("/api/v1/wardrobe/items")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imageMimeType").value("image/png"))
                .andReturn()
                .getResponse()
                .getHeader("Location");

        mockMvc.perform(get(location).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").isNotEmpty())
                .andExpect(jsonPath("$.imageBase64").doesNotExist());
    }

    @Test
    void rejectsBlankCategoryWith400() throws Exception {
        User user = userRepository.save(new User("blank-cat", "blank@aura.app"));
        mockMvc.perform(post("/api/v1/wardrobe/items")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "category": "  ",
                                  "imageBase64": "%s"
                                }
                                """.formatted(PNG_BASE64)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.category").exists());
    }

    @Test
    void rejectsMalformedBase64With400() throws Exception {
        User user = userRepository.save(new User("bad-b64", "badb64@aura.app"));
        mockMvc.perform(post("/api/v1/wardrobe/items")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "category": "shirt",
                                  "imageBase64": "bu-gecerli-base64-degil!!!"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Gecersiz gorsel yuku"));
    }

    @Test
    void rejectsConfidenceAboveOneWith400() throws Exception {
        User user = userRepository.save(new User("conf", "conf@aura.app"));
        mockMvc.perform(post("/api/v1/wardrobe/items")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "category": "shirt",
                                  "categoryConfidence": 1.5,
                                  "imageBase64": "%s"
                                }
                                """.formatted(PNG_BASE64)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.categoryConfidence").exists());
    }

    @Test
    void listsItemsWithoutImagesByDefault() throws Exception {
        User user = userRepository.save(new User("listeci", "listeci@aura.app"));
        String token = bearer(user);
        createItem(user, "shirt");
        createItem(user, "pants");

        mockMvc.perform(get("/api/v1/wardrobe/items")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].category").value("shirt"))
                .andExpect(jsonPath("$[0].imageBase64").doesNotExist())
                .andExpect(jsonPath("$[0].imageDataUri").doesNotExist())
                .andExpect(jsonPath("$[0].imageMimeType").value("image/png"))
                .andExpect(jsonPath("$[0].imageUrl").isNotEmpty());
    }

    @Test
    void listsItemsWithImagesWhenRequested() throws Exception {
        User user = userRepository.save(new User("resimci", "resimci@aura.app"));
        createItem(user, "jacket");

        mockMvc.perform(get("/api/v1/wardrobe/items")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .param("includeImages", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].imageUrl").isNotEmpty())
                .andExpect(jsonPath("$[0].imageBase64").doesNotExist());
    }

    @Test
    void returnsSingleItemWithImage() throws Exception {
        User user = userRepository.save(new User("tekil", "tekil@aura.app"));
        Long itemId = createItem(user, "sneakers");

        mockMvc.perform(get("/api/v1/wardrobe/items/{id}", itemId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(itemId))
                .andExpect(jsonPath("$.category").value("sneakers"))
                .andExpect(jsonPath("$.imageUrl").isNotEmpty())
                .andExpect(jsonPath("$.imageMimeType").value("image/png"))
                .andExpect(jsonPath("$.imageBase64").doesNotExist());
    }

    @Test
    void foreignItemReturns403() throws Exception {
        User owner = userRepository.save(new User("own-item", "own-item@aura.app"));
        User other = userRepository.save(new User("other-item", "other-item@aura.app"));
        Long itemId = createItem(owner, "coat");

        mockMvc.perform(get("/api/v1/wardrobe/items/{id}", itemId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Dolap erisim engeli"));
    }

    @Test
    void returns404ForUnknownItem() throws Exception {
        User user = userRepository.save(new User("miss", "miss@aura.app"));
        mockMvc.perform(get("/api/v1/wardrobe/items/{id}", 987654)
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Dolap parcasi bulunamadi"));
    }

    @Test
    void rawJsonAlreadyNormalizedTrue_callsNormalizeWithSkipOrientation() throws Exception {
        User user = userRepository.save(new User(
                "http-norm-true-" + System.nanoTime(),
                "http-norm-true-" + System.nanoTime() + "@aura.app"));
        mockMvc.perform(post("/api/v1/wardrobe/items")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "category": "t-shirt",
                                  "imageBase64": "%s",
                                  "alreadyNormalized": true
                                }
                                """.formatted(PNG_BASE64)))
                .andExpect(status().isCreated());

        verify(visionGarmentClient, times(1)).normalizeGarmentPng(any(), any(), eq(true));
    }

    @Test
    void rawJsonAlreadyNormalizedFalse_callsNormalizeWithoutSkip() throws Exception {
        User user = userRepository.save(new User(
                "http-norm-false-" + System.nanoTime(),
                "http-norm-false-" + System.nanoTime() + "@aura.app"));
        mockMvc.perform(post("/api/v1/wardrobe/items")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "category": "shirt",
                                  "imageBase64": "%s",
                                  "alreadyNormalized": false
                                }
                                """.formatted(PNG_BASE64)))
                .andExpect(status().isCreated());

        verify(visionGarmentClient, times(1)).normalizeGarmentPng(any(), any(), eq(false));
    }

    @Test
    void rawJsonMissingAlreadyNormalized_callsNormalizeWithoutSkip() throws Exception {
        User user = userRepository.save(new User(
                "http-norm-missing-" + System.nanoTime(),
                "http-norm-missing-" + System.nanoTime() + "@aura.app"));
        mockMvc.perform(post("/api/v1/wardrobe/items")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "category": "jacket",
                                  "imageBase64": "%s"
                                }
                                """.formatted(PNG_BASE64)))
                .andExpect(status().isCreated());

        verify(visionGarmentClient, times(1)).normalizeGarmentPng(any(), any(), eq(false));
    }

    @Test
    void createItem_visionUnusable_returns422AndDoesNotSave() throws Exception {
        User user = userRepository.save(new User(
                "empty-vision-" + System.nanoTime(),
                "empty-vision-" + System.nanoTime() + "@aura.app"));
        when(visionGarmentClient.normalizeGarmentPng(any(), any(), anyBoolean()))
                .thenThrow(new UnusableGarmentException(
                        "empty_mask", "sade bir zeminde cekin"));

        mockMvc.perform(post("/api/v1/wardrobe/items")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"category":"shirt","imageBase64":"%s"}
                                """.formatted(PNG_BASE64)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.rejected_reason").value("empty_mask"))
                .andExpect(jsonPath("$.title").value("Kiyafet kesilemedi"));

        assertThat(wardrobeItemRepository.findByUserId(user.getId())).isEmpty();
    }

    @Test
    void createItem_blankCanvasBypassingAnalyze_returns422() throws Exception {
        User user = userRepository.save(new User(
                "empty-src-" + System.nanoTime(),
                "empty-src-" + System.nanoTime() + "@aura.app"));
        when(visionGarmentClient.normalizeGarmentPng(any(), any(), anyBoolean()))
                .thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/wardrobe/items")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"category":"shirt","imageBase64":"%s"}
                                """.formatted(solidPngBase64(new Color(214, 206, 196)))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.rejected_reason").value("empty_mask"));

        assertThat(wardrobeItemRepository.findByUserId(user.getId())).isEmpty();
    }

    @Test
    void createItem_decompressionBombHeader_returns413AndDoesNotCallVision() throws Exception {
        User user = userRepository.save(new User(
                "bomb-" + System.nanoTime(),
                "bomb-" + System.nanoTime() + "@aura.app"));
        String bombB64 = Base64.getEncoder().encodeToString(PngBombs.declaredSize(30_000, 30_000));

        mockMvc.perform(post("/api/v1/wardrobe/items")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"category":"shirt","imageBase64":"%s"}
                                """.formatted(bombB64)))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.rejected_reason").value("image_too_large"))
                .andExpect(jsonPath("$.title").value("Gorsel cok buyuk"));

        verify(visionGarmentClient, never()).normalizeGarmentPng(any(), any(), anyBoolean());
        assertThat(wardrobeItemRepository.findByUserId(user.getId())).isEmpty();
    }

    private static String solidPngBase64(Color color) throws Exception {
        BufferedImage image = new BufferedImage(64, 80, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(color);
        graphics.fillRect(0, 0, 64, 80);
        graphics.dispose();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ImageIO.write(image, "png", buffer);
        return Base64.getEncoder().encodeToString(buffer.toByteArray());
    }

    private Long createItem(User user, String category) throws Exception {
        String body = """
                {"category": "%s", "imageBase64": "%s"}
                """.formatted(category, PNG_BASE64);

        String response = mockMvc.perform(post("/api/v1/wardrobe/items")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode node = objectMapper.readTree(response);
        return node.get("id").asLong();
    }
}
