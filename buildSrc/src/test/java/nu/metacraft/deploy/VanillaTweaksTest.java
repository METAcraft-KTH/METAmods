package nu.metacraft.deploy;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VanillaTweaksTest {
    @Test
    void namesAPackAsThePickerDoes() {
        assertArrayEquals(new String[]{"26.3", "decorative/cosmetic", "more mob heads"},
                VanillaTweaks.parse(URI.create("vanillatweaks:26.3/decorative%2Fcosmetic/more%20mob%20heads")));
    }

    @Test
    void refusesAnythingButThreeNames() {
        assertThrows(DeployException.class, () -> VanillaTweaks.parse(URI.create("vanillatweaks:26.3/more%20mob%20heads")));
        assertThrows(DeployException.class, () -> VanillaTweaks.parse(URI.create("vanillatweaks:26.3/decorative/cosmetic/more%20mob%20heads")));
        assertThrows(DeployException.class, () -> VanillaTweaks.parse(URI.create("vanillatweaks:26.3//more%20mob%20heads")));
    }
}
