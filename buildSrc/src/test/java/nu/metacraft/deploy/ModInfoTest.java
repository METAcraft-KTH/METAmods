package nu.metacraft.deploy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModInfoTest {
    @TempDir
    Path tmp;

    @Test
    void readsIdVersionAndDependsFromAJar() throws IOException {
        Path jar = TestJars.modJar(tmp.resolve("plots.jar"), "metacraft-plots", "1.0.0", "metacraft-core", "fabric-api");
        assertEquals(new ModInfo("metacraft-plots", "1.0.0", Set.of("fabric-api", "metacraft-core")), ModInfo.read(jar));
    }

    @Test
    void readsTheIdOfASourceFabricModJsonWithPlaceholders() throws IOException {
        Path json = tmp.resolve("fabric.mod.json");
        Files.writeString(json, "{\"schemaVersion\":1,\"id\":\"faster-minecarts\",\"version\":\"${version}\"}", StandardCharsets.UTF_8);
        assertEquals("faster-minecarts", ModInfo.readSourceId(json));
    }

    @Test
    void failsForAJarWithoutFabricModJson() throws IOException {
        Path jar = TestJars.plainJar(tmp.resolve("lib.jar"));
        DeployException e = assertThrows(DeployException.class, () -> ModInfo.read(jar));
        assertTrue(e.getMessage().contains("lib.jar has no fabric.mod.json"), e.getMessage());
    }

    @Test
    void sha256OfKnownInput() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                Sha256.of("abc".getBytes(StandardCharsets.UTF_8)));
    }
}
