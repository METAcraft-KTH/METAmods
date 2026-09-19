package nu.metacraft.rivals.pack;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.minecraft.core.Direction;
import nu.metacraft.rivals.PaintColor;
import nu.metacraft.rivals.Rivals;
import nu.metacraft.rivals.paint.PaintStates;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

/**
 * The resource pack: the mod's own assets (gun model, palette, lang) plus the generated paint art —
 * bit-carrying textures, the six face quads and a variants blockstate override per donor block.
 * Required, because without it players see sculk veins, resin clumps, redstone dust, pale moss
 * carpet and stone buttons where the paint is. Also ships two core shader overrides, each vanilla's own plus the same paint gloss
 * keyed on the marker alpha: terrain.vsh/terrain.fsh, which is what draws chunk geometry in 26.3 (not
 * block.*), and item.vsh/item.fsh, which is what draws the block displays
 * {@link nu.metacraft.rivals.paint.PaintDisplays} hangs on stairs, slabs, fences and panes. And the ink
 * on the screen: the {@code end_of_frame} post effect, its two shaders and the data LED's texture
 * ({@link InkArt}).
 */
public final class RivalsPack {
	/** The core shader pairs the pack replaces, both of them vanilla's plus the RIVALS_GLOSS block. */
	public static final List<String> SHADERS = List.of("terrain.vsh", "terrain.fsh", "item.vsh", "item.fsh");

	private RivalsPack() {}

	public static void init() {
		PolymerResourcePackUtils.addModAssets(Rivals.MOD_ID);
		PolymerResourcePackUtils.markAsRequired();
		PolymerResourcePackUtils.RESOURCE_PACK_CREATION_EVENT.register(builder -> {
			Map<String, byte[]> paint = PaintArt.packFiles();
			Map<String, byte[]> ink = InkArt.packFiles();
			paint.forEach(builder::addData);
			ink.forEach(builder::addData);
			// Name every group the paint map holds, with its own count, so the total adds up when read:
			// bit textures + face models + wrappers + mask models + the empty model + donor overrides.
			int colors = PaintColor.values().length;
			Rivals.LOGGER.info(
					"[{}] pack: {} paint files ({} bit textures, {} face models, {} wrappers, {} mask models, 1 empty model, {} donor overrides), {} ink files, terrain and item shaders",
					Rivals.MOD_ID, paint.size(), colors * PaintArt.BITS, Direction.values().length,
					colors * PaintArt.BITS * Direction.values().length, colors * PaintStates.SPLAT_PER_COLOR,
					PaintStates.DONORS.size(), ink.size());
			for (String name : SHADERS) builder.addData("assets/minecraft/shaders/core/" + name, shader(name));
		});
	}

	/** A shader file from the mod's resources, as shipped under assets/minecraft/shaders/core. */
	public static byte[] shader(String name) {
		return bytes("/rivals_shaders/" + name, name);
	}

	/** One of the mod's own resources, read whole. Missing is a bug in the jar, not a runtime condition. */
	static byte[] bytes(String path, String what) {
		try (InputStream in = RivalsPack.class.getResourceAsStream(path)) {
			if (in == null) throw new IllegalStateException("[" + Rivals.MOD_ID + "] missing resource " + what);
			return in.readAllBytes();
		} catch (IOException e) {
			throw new UncheckedIOException("could not read " + what, e);
		}
	}
}
