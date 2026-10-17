package nu.metacraft.deploy;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServersConfigTest {
    @Test
    void theRealFileMatchesTheSpec() {
        ServersConfig config = ServersConfig.read(Path.of("../deploy/servers.json"));
        assertEquals(List.of("test"), config.branches().get("dev"));
        assertEquals(List.of("survival", "test"), config.branches().get("prod"));
        assertEquals(List.of("event"), config.branches().get("minigame"));
        assertEquals(new ServersConfig.Server("mc.datasektionen.se", 2022, "github-actions.098974a0"), config.server("test"));
        assertEquals(new ServersConfig.Server("mc.datasektionen.se", 2022, "github-actions.604966da"), config.server("survival"));
        assertEquals(new ServersConfig.Server("pterodactyl.kth.it", 3022, "github-actions.a98edceb"), config.server("event"));
    }

    @Test
    void unknownServerFails() {
        ServersConfig config = ServersConfig.parse("{\"branches\":{},\"servers\":{}}");
        assertTrue(assertThrows(DeployException.class, () -> config.server("tset")).getMessage().startsWith("unknown server tset"));
    }

    @Test
    void aBranchNamingAMissingServerFails() {
        DeployException e = assertThrows(DeployException.class,
                () -> ServersConfig.parse("{\"branches\":{\"dev\":[\"test\"]},\"servers\":{}}"));
        assertTrue(e.getMessage().contains("branch dev deploys to test"), e.getMessage());
    }
}
