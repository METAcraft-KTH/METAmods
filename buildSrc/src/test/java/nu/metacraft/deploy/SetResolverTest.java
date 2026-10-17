package nu.metacraft.deploy;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SetResolverTest {
    // Shaped like the real mods/: every mod gets metacraft-lib; addCoreDependencies adds metacraft-core.
    private static final Map<String, Set<String>> DEPS = Map.of(
            "metacraft-lib", Set.of(),
            "metacraft-core", Set.of("metacraft-lib"),
            "metacraft-resource-packs", Set.of("metacraft-lib"),
            "metacraft-zones", Set.of("metacraft-lib", "metacraft-core", "metacraft-resource-packs"),
            "metacraft-plots", Set.of("metacraft-lib", "metacraft-core", "metacraft-zones"),
            "faster-minecarts", Set.of("metacraft-lib"));

    @Test
    void addsDependenciesTransitively() {
        assertEquals(new TreeSet<>(List.of("metacraft-core", "metacraft-lib", "metacraft-plots",
                        "metacraft-resource-packs", "metacraft-zones")),
                SetResolver.resolve("survival", List.of("metacraft-plots"), DEPS));
    }

    @Test
    void keepsListedModsAndSharedDependenciesOnce() {
        assertEquals(new TreeSet<>(List.of("faster-minecarts", "metacraft-lib")),
                SetResolver.resolve("test", List.of("faster-minecarts", "metacraft-lib"), DEPS));
    }

    @Test
    void failsOnAnUnknownProject() {
        DeployException e = assertThrows(DeployException.class,
                () -> SetResolver.resolve("survival", List.of("better-pet"), DEPS));
        assertTrue(e.getMessage().startsWith("unknown project better-pet in deploy/survival.txt"), e.getMessage());
    }

    @Test
    void terminatesOnACycle() {
        Map<String, Set<String>> cyclic = Map.of("a", Set.of("b"), "b", Set.of("a"));
        assertEquals(new TreeSet<>(List.of("a", "b")), SetResolver.resolve("test", List.of("a"), cyclic));
    }
}
