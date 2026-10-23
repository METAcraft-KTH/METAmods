package se.metacraft.shaders;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.api.ModInitializer;

/**
 * Shaders more than one of our mods draw with, sent to clients in the Polymer resource pack. For now that is one
 * item shader for rifts ({@code assets/minecraft/shaders/core/item.{vsh,fsh}} with
 * {@code assets/metacraft/shaders/include/{rift_common,void_rift,nether_rift}.glsl}): it only changes items whose
 * tint carries the rift markers (#FExxF9 to #FExxFD), which metacraft-qol's void anchor and nether-rifts emit, and
 * draws everything else as vanilla. A pack holds one item shader, so the rifts share it here.
 */
public class METAcraftShaders implements ModInitializer {

	public static final String MODID = "metacraft-shaders";

	@Override
	public void onInitialize() {
		PolymerResourcePackUtils.addModAssets(MODID);
		PolymerResourcePackUtils.markAsRequired();
	}

}
