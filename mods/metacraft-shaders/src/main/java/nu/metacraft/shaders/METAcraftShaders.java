package nu.metacraft.shaders;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.api.ModInitializer;

/**
 * The vanilla shaders our mods draw with, sent to clients in the Polymer resource pack. A pack holds one copy of
 * each vanilla shader and Polymer silently keeps the last mod's, so every override lives here, with each mod's
 * code in an include under its own namespace, and the build fails if another module ships
 * {@code assets/minecraft/shaders}. Each effect only changes what is marked as its own and draws everything else as
 * vanilla:
 * <ul>
 *   <li>the item shader ({@code assets/minecraft/shaders/core/item.{vsh,fsh}} with
 *   {@code assets/metacraft/shaders/include/{rift_common,void_rift,nether_rift}.glsl}): rifts, on items whose tint
 *   carries the rift markers (#FExxF9 to #FExxFD), which metacraft-qol's void anchor and nether-rifts emit;</li>
 *   <li>the entity shader ({@code assets/minecraft/shaders/core/entity.{vsh,fsh}}): ovvar's patches
 *   ({@code assets/ovvar/shaders/include/ovvar.glsl}) on its 256×128 garment textures.</li>
 * </ul>
 */
public class METAcraftShaders implements ModInitializer {

	public static final String MODID = "metacraft-shaders";

	@Override
	public void onInitialize() {
		PolymerResourcePackUtils.addModAssets(MODID);
		PolymerResourcePackUtils.markAsRequired();
	}

}
