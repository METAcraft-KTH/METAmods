import nu.metacraft.qol.void_anchor.rift.Rift;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The pack has every crack shape a rift can pick, and no more, and the shatter rift's rays and core. */
public class RiftAssetsTests {

	@Test
	public void everyVariantIsInThePack() {
		for (int i = 0; i < Rift.VARIANTS; i++) {
			for (var path : new String[]{"items/rift_%d.json", "models/item/rift_%d.json", "textures/item/rift_%d.png"}) {
				var file = "assets/metacraft/" + path.formatted(i);
				assertNotNull(getClass().getClassLoader().getResource(file), "missing " + file);
			}
		}
		for (int i = 0; i < Rift.RAY_VARIANTS; i++) {
			for (var path : new String[]{"items/rift_ray_%d.json", "models/item/rift_ray_%d.json", "textures/item/rift_ray_%d.png"}) {
				var file = "assets/metacraft/" + path.formatted(i);
				assertNotNull(getClass().getClassLoader().getResource(file), "missing " + file);
			}
		}
		for (var file : new String[]{"items/rift_core.json", "models/item/rift_core.json", "textures/item/rift_core.png"}) {
			assertNotNull(getClass().getClassLoader().getResource("assets/metacraft/" + file), "missing " + file);
		}
		var extra = "assets/metacraft/items/rift_" + Rift.VARIANTS + ".json";
		assertNull(getClass().getClassLoader().getResource(extra), extra + " is never picked: raise Rift.VARIANTS");
	}

}
