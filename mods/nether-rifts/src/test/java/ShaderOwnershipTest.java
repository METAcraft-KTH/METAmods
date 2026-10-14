import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Only one mod may ship vanilla's item shaders, or the merged pack keeps one at random and a rift goes dark. */
public class ShaderOwnershipTest {

	private static final Path MODS = Path.of("..");

	@Test
	public void onlyCoreOverridesItemShaders() throws IOException {
		try (var modules = Files.list(MODS)) {
			List<String> owners = modules
					.filter(m -> Files.exists(m.resolve("src/main/resources/assets/minecraft/shaders/core/item.fsh"))
							|| Files.exists(m.resolve("src/main/resources/assets/minecraft/shaders/core/item.vsh")))
					.map(m -> m.getFileName().toString())
					.sorted()
					.toList();
			assertEquals(List.of("metacraft-core"), owners);
		}
	}

	@Test
	public void everyIncludeShipsWithCore() throws IOException {
		var core = MODS.resolve("metacraft-core/src/main/resources/assets");
		var fsh = Files.readString(core.resolve("minecraft/shaders/core/item.fsh"));
		for (var include : List.of("rift_common", "void_rift", "nether_rift")) {
			assertTrue(fsh.contains("#include <metacraft:" + include + ".glsl>"), "item.fsh doesn't include " + include);
			assertTrue(Files.exists(core.resolve("metacraft/shaders/include/" + include + ".glsl")), include + ".glsl is missing");
		}
	}
}
