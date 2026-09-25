package nu.metacraft.deploy;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DependsCheckTest {
    private static final Set<String> REPO = Set.of("metacraft-lib", "metacraft-zones", "portal-blocker");

    @Test
    void passesWhenInRepoDependsAreInTheSet() {
        Map<String, ModInfo> set = Map.of(
                "portal-blocker", new ModInfo("portal-blocker", "1", Set.of("metacraft-zones", "fabric-api")),
                "metacraft-zones", new ModInfo("metacraft-zones", "1", Set.of("metacraft-lib")),
                "metacraft-lib", new ModInfo("metacraft-lib", "1", Set.of("minecraft")));
        assertDoesNotThrow(() -> DependsCheck.check("survival", set, REPO));
    }

    @Test
    void namesTheModAndTheMissingDependency() {
        Map<String, ModInfo> set = Map.of(
                "portal-blocker", new ModInfo("portal-blocker", "1", Set.of("metacraft-zones", "polymer-core")));
        DeployException e = assertThrows(DeployException.class, () -> DependsCheck.check("survival", set, REPO));
        assertEquals("portal-blocker needs metacraft-zones, which is not on survival's list", e.getMessage());
    }
}
