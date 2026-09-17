package app.aura.backend.engine;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ClothTypeMapperTest {

    @ParameterizedTest
    @CsvSource({
            "t-shirt, upper",
            "T-Shirt, upper",
            "shirt, upper",
            "jacket, upper",
            "TOPS, upper",
            "hoodie, upper",
            "pants, lower",
            "jeans, lower",
            "skirt, lower",
            "BOTTOMS, lower",
            "dress, overall",
            "jumpsuit, overall",
            "romper, overall"
    })
    void mapsKnownCategories(String category, String expected) {
        assertThat(ClothTypeMapper.fromCategory(category)).isEqualTo(expected);
    }

    @Test
    void blankAndUnknownDefaultToUpper() {
        assertThat(ClothTypeMapper.fromCategory(null)).isEqualTo(ClothTypeMapper.UPPER);
        assertThat(ClothTypeMapper.fromCategory("   ")).isEqualTo(ClothTypeMapper.UPPER);
        assertThat(ClothTypeMapper.fromCategory("perfume bottle")).isEqualTo(ClothTypeMapper.UPPER);
        assertThat(ClothTypeMapper.fromCategory("sneakers")).isEqualTo(ClothTypeMapper.UPPER);
    }
}
