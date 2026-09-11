package metacraft.ovvar.datagen;

import com.google.common.hash.Hashing;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import metacraft.ovvar.Ovvar;
import metacraft.ovvar.content.Chapter;
import metacraft.ovvar.content.Looks;
import metacraft.ovvar.content.ModContent;
import metacraft.ovvar.content.OvveFeet;
import metacraft.ovvar.content.PatchItem;
import metacraft.ovvar.content.PatchPieces;
import metacraft.ovvar.content.Patches;
import metacraft.ovvar.content.Piece;
import metacraft.ovvar.content.Placement;
import metacraft.ovvar.content.Spot;
import metacraft.ovvar.pack.EquipmentJson;
import metacraft.ovvar.pack.Trims;
import metacraft.ovvar.sewing.Outline;
import metacraft.ovvar.sewing.Seam;
import metacraft.ovvar.sewing.SewingFont;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static metacraft.ovvar.datagen.J.arr;
import static metacraft.ovvar.datagen.J.obj;

/**
 * Everything the client needs, derived from the art in {@code art/ovvar}:
 *
 * <ul>
 *   <li>armour layer textures cut out of the website's skin overlays (64×64 skin layout → 64×32 armour layout,
 *	   then doubled to 128×64 so patch art gets 8×8 texels per cell — {@link Spot#DETAIL};
 *	   the boxes the armour model reads — body (16,16), right arm (40,16), right leg (0,16) — sit at the same
 *	   coordinates in both, and the left limbs are the model's mirrors of the right, so nothing moves),</li>
 *   <li>one texture per (cell, patch) placement, drawn on one side of the model by the shader, and per half a
 *	   preview texture holding every patch's art plus the tables the shader uses to draw the dye colour's slots,</li>
 *   <li>the equipment definition of every garment half with nothing sewn on (the sewn ones come from Combos),</li>
 *   <li>item definitions, models, icons and lang for every ovve, top and patch.</li>
 * </ul>
 */
public final class GeneratedAssets implements DataProvider {
	private static final String MOD = Ovvar.MOD_ID;

	/** Skin-layout boxes (x, y, w, h) that the armour model draws, and which garment owns each. */
	private static final int[] BODY = {16, 16, 24, 16};
	private static final int[] RIGHT_ARM = {40, 16, 16, 16};
	private static final int[] RIGHT_LEG = {0, 16, 16, 16};
	/** The skin's second layer for each, and the left limbs (base, second layer) — the website draws these in 3D. */
	private static final int[] BODY_OUTER = {16, 32, 24, 16};
	private static final int[] RIGHT_ARM_OUTER = {40, 32, 16, 16}, RIGHT_LEG_OUTER = {0, 32, 16, 16};
	private static final int[] LEFT_ARM = {32, 48, 16, 16}, LEFT_ARM_OUTER = {48, 48, 16, 16};
	private static final int[] LEFT_LEG = {16, 48, 16, 16}, LEFT_LEG_OUTER = {0, 48, 16, 16};
	/** The trousers' share of the body box: the bottom two texel rows of its side faces (the waistband). */
	private static final int[] WAIST = {16, 30, 24, 2};
	/**
	 * Garment and patch textures are the armour layout at {@link Spot#DETAIL} texels per skin
	 * texel ({@code W}×{@code H}); base garments cut from the skins are upscaled to it, patch art
	 * is drawn at it. Every texel position the shader knows (marker, tables, library) scales with it.
	 */
	private static final int D = Spot.DETAIL, W = 64 * D, H = 32 * D;
	/** The texel our core shader checks before treating a texture as ours: magenta at alpha 2. */
	private static final int MARKER_X = W - 1, MARKER_Y = H / 2 - 1, MARKER = 0x02FF00FF;

	private final Path root, assets, data;
	private final List<CompletableFuture<?>> writes = new ArrayList<>();
	private CachedOutput out;

	public GeneratedAssets(FabricPackOutput output) {
		this.root = output.getOutputFolder();
		this.assets = output.getOutputFolder(PackOutput.Target.RESOURCE_PACK).resolve(MOD);
		this.data = output.getOutputFolder(PackOutput.Target.DATA_PACK).resolve(MOD);
	}

	@Override
	public String getName() {
		return "Ovvar art-derived assets";
	}

	@Override
	public CompletableFuture<?> run(CachedOutput output) {
		this.out = output;
		writes.clear();
		Map<String, String> lang = new LinkedHashMap<>();
		lang.put("itemGroup." + MOD, "Ovvar");
		Tex icon = art("icon");
		require(icon.width == 16 && icon.height == 16, "icon.png is not 16×16");

		// Patches: art, an icon, an item.
		Map<String, Tex> arts = new LinkedHashMap<>();
		for (Patches.Patch patch : Patches.all()) {
			Tex art = art("patches/" + patch.id());
			require(art.width == patch.width() && art.height == patch.height(),
					"patches/" + patch.id() + ".png is " + art.width + "×" + art.height + ", the catalogue says " + patch.width() + "×" + patch.height());
			arts.put(patch.id(), art);
			String name = ModContent.patchId(patch).getPath();
			item(name, icon(art));
			// Flat pieces for the stand displays: the art 1:1 in the sprite's centre, one model per
			// piece any cell cuts it into (the whole art included).
			Map<String, PatchPieces.Piece> pieces = new LinkedHashMap<>();
			for (Spot spot : Spot.values()) {
				if (patch.fits(spot)) for (PatchPieces.Piece piece : PatchPieces.of(spot, patch)) pieces.putIfAbsent(piece.key(), piece);
			}
			for (PatchPieces.Piece piece : pieces.values()) {
				int ox = (16 - art.width) / 2, oy = (16 - art.height) / 2;
				Tex sprite = Tex.blank(16, 16).blit(art, piece.x0(), piece.y0(), piece.x1() - piece.x0(), piece.y1() - piece.y0(), ox + piece.x0(), oy + piece.y0());
				sprite(PatchItem.flatModel(name, piece), sprite);
			}
			lang.put("item." + MOD + "." + name, patch.name() + " patch");
		}

		// One static texture per (cell, patch): the art on its cell, drawn on one side only by the
		// shader (marker texel: kind 1, side). The seat is two of them, one per leg.
		int placementTextures = 0;
		for (Spot spot : Spot.values()) {
			for (Patches.Patch patch : Patches.all()) {
				if (!patch.fits(spot)) continue;
				Tex art = arts.get(patch.id());
				String dir = "textures/entity/equipment/" + spot.piece.layer + "/";
				if (spot == Spot.SEAT) {
					Tex r = Tex.blank(W, H).blit(art, 0, 0, Spot.PX, Spot.PX, spot.u * D, spot.v * D);
					Tex l = Tex.blank(W, H).blit(art, Spot.PX, 0, Spot.PX, Spot.PX, spot.u * D, spot.v * D).flipX(spot.u * D, spot.v * D, Spot.PX, Spot.PX);
					png(assets.resolve(dir + "patch/seat/" + patch.id() + "_r.png"), sided(r, spot, Spot.Side.RIGHT));
					png(assets.resolve(dir + "patch/seat/" + patch.id() + "_l.png"), sided(l, spot, Spot.Side.LEFT));
					placementTextures += 2;
					continue;
				}
				// Centred on the cell, hanging over it if bigger, clipped to the part's side rows;
				// a left cell's art is mirrored (the model mirrors the left limb).
				Tex placed = placed(spot, spot.side == Spot.Side.LEFT ? art.flipX() : art, spot.u * D + patch.offsetX());
				png(assets.resolve(dir + "patch/" + spot.id() + "/" + patch.id() + ".png"), sided(placed, spot, spot.side));
				placementTextures++;
			}
		}

		// The trim channel (the ghost preview): one trim pattern per (cell, plain patch). Limb cells
		// are alpha-tagged with their side (the palette permutation keeps a texel's alpha). Vanilla
		// draws trims, so the squeeze to square pixels (ovvar.glsl) is baked in here, to the texel.
		List<String> trimTextures = new ArrayList<>();
		for (Spot spot : Spot.values()) {
			for (Patches.Patch patch : Patches.all()) {
				if (!patch.fits(spot) || spot == Spot.SEAT) continue;
				Placement placement = new Placement(spot, patch);
				String name = Trims.patternName(placement);
				Tex art = arts.get(patch.id());
				Tex tex = placedWrapped(spot, spot.side == Spot.Side.LEFT ? art.flipX() : art, spot.u * D + patch.offsetX());
				if (spot.side == Spot.Side.LEFT) tex = tex.tagOpaque(Trims.ALPHA_LEFT);
				if (spot.side == Spot.Side.RIGHT) tex = tex.tagOpaque(Trims.ALPHA_RIGHT);
				png(assets.resolve("textures/trims/entity/" + spot.piece.layer + "/" + name + ".png"), tex);
				trimTextures.add(MOD + ":trims/entity/" + spot.piece.layer + "/" + name);
				json(data.resolve("trim_pattern/" + name + ".json"),
						obj("asset_id", MOD + ":" + name, "decal", false, "description", obj("text", patch.name() + " on the " + spot.label())));
			}
		}
		// Materials are colour permutations of a key palette, so the key is every colour any patch
		// uses: "patch" maps each to itself, "ghost" (the preview) to a washed-out version.
		List<Integer> colours = new ArrayList<>();
		for (Tex art : arts.values()) for (int c : art.opaqueColours()) if (!colours.contains(c)) colours.add(c);
		Tex key = Tex.blank(colours.size(), 1), patchPalette = key, ghostPalette = key;
		for (int i = 0; i < colours.size(); i++) {
			int c = colours.get(i);
			key = key.with(i, 0, c);
			patchPalette = patchPalette.with(i, 0, c);
			ghostPalette = ghostPalette.with(i, 0, Tex.mix(c, 0xFFFFFFFF, 0.6));
		}
		String palettes = "textures/trims/color_palettes/";
		png(assets.resolve(palettes + "key.png"), key);
		png(assets.resolve(palettes + Trims.MATERIAL + ".png"), patchPalette);
		png(assets.resolve(palettes + Trims.GHOST + ".png"), ghostPalette);
		json(data.resolve("trim_material/" + Trims.MATERIAL + ".json"), obj("asset_name", Trims.MATERIAL, "description", obj("text", "Patch")));
		json(data.resolve("trim_material/" + Trims.GHOST + ".json"), obj("asset_name", Trims.GHOST, "description", obj("text", "Patch (not sewn yet)")));
		json(assets.getParent().resolve("minecraft/atlases/armor_trims.json"), obj("sources", arr(obj(
				"type", "minecraft:paletted_permutations",
				"textures", arr(trimTextures.toArray()),
				"palette_key", MOD + ":trims/color_palettes/key",
				"permutations", obj(Trims.MATERIAL, MOD + ":trims/color_palettes/" + Trims.MATERIAL, Trims.GHOST, MOD + ":trims/color_palettes/" + Trims.GHOST)))));
		Ovvar.LOGGER.info("[{} datagen] {} trim patterns", MOD, trimTextures.size());

		// The preview layer per half: every instant design's art in the library (a block of cells
		// its size), the cell and design tables, marker kind 2. The shader draws what the dye
		// colour's slots name.
		Map<String, int[]> library = new LinkedHashMap<>();
		boolean[][] taken = new boolean[LIBRARY_COLUMNS][LIBRARY_ROWS];
		for (Patches.Patch patch : Patches.all()) {
			if (Patches.code(patch) > Looks.INSTANT_DESIGNS) continue;   // never in the dye colour: no library entry
			int w = (patch.width() + Spot.PX - 1) / Spot.PX, h = (patch.height() + Spot.PX - 1) / Spot.PX;
			library.put(patch.id(), libraryBlock(taken, w, h, patch.id()));
		}
		// The legs' preview is also drawn by the boots pass (the outer model, inflate 1), as the
		// second dye channel: the same texture with that layer's texel, in the humanoid folder.
		record PreviewTarget(Piece piece, String layer, String name, Piece inflateAs) {}
		List<PreviewTarget> previews = new ArrayList<>();
		for (Piece piece : Piece.values()) previews.add(new PreviewTarget(piece, piece.layer, EquipmentJson.previewTexture(piece), piece));
		previews.add(new PreviewTarget(Piece.BOTTOM, Piece.TOP.layer, EquipmentJson.FEET_PREVIEW, Piece.TOP));
		for (PreviewTarget target : previews) {
			List<Spot> cells = Spot.cells(target.piece);
			require(cells.size() <= TABLE_SIZE && Looks.INSTANT_DESIGNS <= TABLE_SIZE, "the preview tables hold " + TABLE_SIZE + " cells and designs");
			Tex tex = Tex.blank(W, H).with(MARKER_KIND_X, MARKER_Y, rgb(KIND_PREVIEW, cells.size(), Looks.INSTANT_DESIGNS));
			for (Patches.Patch patch : Patches.all()) {
				int[] at = library.get(patch.id());
				if (at == null) continue;
				Tex art = arts.get(patch.id());
				tex = tex.blit(art, 0, 0, art.width, art.height, at[0] * D, at[1] * D);
				int design = Patches.code(patch) - 1;
				tex = tex.with(PATCH_TABLE_X + design / 16, design % 16, rgb(at[0] * D, at[1] * D, patch.cells()));
				tex = tex.with(PATCH_TABLE_X + TABLE_COLUMNS + design / 16, design % 16, rgb(art.width, art.height, 0));
			}
			for (int index = 0; index < cells.size(); index++) {
				Spot spot = cells.get(index);
				tex = tex.with(CELL_TABLE_X + index / 16, index % 16, rgb(spot.u * D, spot.v * D, spot.side.ordinal()));
			}
			require(tex.get(BLANK_X, BLANK_Y) == 0, "the preview texture draws on the blank texel");
			png(assets.resolve("textures/entity/equipment/" + target.layer + "/" + target.name + ".png"), marked(tex, target.inflateAs));
		}
		for (String material : OvveFeet.MATERIALS) {
			require(Vanilla.exists("assets/minecraft/textures/entity/equipment/humanoid/" + material + ".png"), "no vanilla equipment texture for " + material);
		}
		for (String material : concat(OvveFeet.NONE, OvveFeet.MATERIALS)) {
			json(assets.resolve("equipment/feet/" + material + ".json"), JsonParser.parseString(EquipmentJson.feetJson(material)));
		}
		Ovvar.LOGGER.info("[{} datagen] {} placement textures, {} patches in the preview library", MOD, placementTextures, library.size());

		Map<Chapter, Integer> chapterColours = new LinkedHashMap<>();
		for (Chapter chapter : Chapter.values()) {
			Tex overlay = overlay(chapter.overlay, chapter);
			int colour = overlay.dominant();
			chapterColours.put(chapter, colour);

			// The top: body and sleeves, on the chest slot's layer.
			Tex top = Tex.blank(64, 32).blit(overlay, BODY[0], BODY[1], BODY[2], BODY[3], BODY[0], BODY[1])
					.blit(overlay, RIGHT_ARM[0], RIGHT_ARM[1], RIGHT_ARM[2], RIGHT_ARM[3], RIGHT_ARM[0], RIGHT_ARM[1]);
			require(!top.isEmpty(), chapter.overlay + ".png has an empty body or arm box");
			layer(chapter, Piece.TOP, "top", withLeft(top, RIGHT_ARM, overlay, LEFT_ARM).scale(D));
			equipment(chapter, Piece.TOP, false);

			// The bottom: legs and waistband, on the legs slot's layer; under the top when it's up.
			Tex bottom = Tex.blank(64, 32).blit(overlay, RIGHT_LEG[0], RIGHT_LEG[1], RIGHT_LEG[2], RIGHT_LEG[3], RIGHT_LEG[0], RIGHT_LEG[1])
					.blit(overlay, WAIST[0], WAIST[1], WAIST[2], WAIST[3], WAIST[0], WAIST[1]);
			require(!bottom.isEmpty(), chapter.overlay + ".png has an empty leg box");
			layer(chapter, Piece.BOTTOM, "bottom", withLeft(bottom, RIGHT_LEG, overlay, LEFT_LEG).scale(D));
			equipment(chapter, Piece.BOTTOM, false);

			if (chapter.rollable) {
				// Rolled down: legs plus the top hanging at the waist, all on the legs slot's layer.
				Tex nercabbad;
				if (chapter.nercabbadArmour != null) {
					// A leggings texture drawn as such (PolymITer's), shifted to the chapter's colour — hue
					// and saturation from the website overlay's main colour, its own shading scaled to
					// that colour's brightness. No left-limb art, so the left leg mirrors the right.
					Tex armour = art(chapter.nercabbadArmour);
					require(armour.width == 64 && armour.height == 32, chapter.nercabbadArmour + ".png is not a 64×32 armour texture");
					armour = armour.tinted(colour).brightened(Tex.brightness(colour) / Tex.brightness(armour.dominant()));
					nercabbad = withLeft(armour, RIGHT_LEG, Tex.blank(64, 64), LEFT_LEG);
				} else {
					Tex rolled = overlay(chapter.nercabbadOverlay, chapter);
					Tex cut = Tex.blank(64, 32).blit(rolled, RIGHT_LEG[0], RIGHT_LEG[1], RIGHT_LEG[2], RIGHT_LEG[3], RIGHT_LEG[0], RIGHT_LEG[1])
							.blit(rolled, BODY[0], BODY[1], BODY[2], BODY[3], BODY[0], BODY[1]);
					nercabbad = withLeft(cut, RIGHT_LEG, rolled, LEFT_LEG);
				}
				layer(chapter, Piece.BOTTOM, "bottom_nercabbad", nercabbad.scale(D));
				equipment(chapter, Piece.BOTTOM, true);
			}

			String ovve = ModContent.ovveId(chapter).getPath();
			String topItem = ModContent.topId(chapter).getPath();
			Tex tinted = icon.tinted(colour);
			item(ovve, tinted);
			item(topItem, Tex.blank(16, 16).blit(tinted, 0, 0, 16, 8, 0, 0));
			String feetItem = ModContent.feetId(chapter).getPath();
			item(feetItem, Tex.blank(16, 16).blit(tinted, 0, 12, 16, 4, 0, 12));
			lang.put("item." + MOD + "." + ovve, chapter.name + " " + chapter.garmentWord());
			lang.put("item." + MOD + "." + topItem, chapter.name + " " + chapter.garmentWord() + " (top)");
			lang.put("item." + MOD + "." + feetItem, chapter.name + " " + chapter.garmentWord() + " (cuffs)");
		}
		Ovvar.LOGGER.info("[{} datagen] {} patches, {} cells, {} chapters", MOD, Patches.all().size(), Spot.values().length, Chapter.values().length);

		sewingFont(chapterColours, arts);

		JsonObject langJson = new JsonObject();
		lang.forEach(langJson::addProperty);
		json(assets.resolve("lang/en_us.json"), langJson);
		// Sewing pinned patches at the smithing table (SewRecipe): the one recipe, nothing to configure.
		json(data.resolve("recipe/sew.json"), obj("type", MOD + ":sew"));
		return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
	}

	private static List<String> concat(String first, List<String> rest) {
		List<String> out = new ArrayList<>();
		out.add(first);
		out.addAll(rest);
		return out;
	}

	private static int rgb(int r, int g, int b) {
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}

	/** The exit band's text and the flat patch it sits on: dark on light cloth, so it does not read as a disabled button. */
	private static final int BAND_TEXT = 0xFF3A2620, BAND_FLAT = 0xFFE0CFAC;

	/**
	 * The stitching dialog's font ({@link SewingFont}) and the outlines its seams follow
	 * ({@link Outline}). The art in {@code art/ovvar/sewing} is the raw material: the cloth tile
	 * is recoloured in every chapter's colour, the needle mirrored and turned for its four
	 * directions, each patch's art scaled up whole, and the exit band gets its text in the vanilla
	 * font. Each patch's outline is traced from its art's opaque texels.
	 */
	private void sewingFont(Map<Chapter, Integer> chapterColours, Map<String, Tex> arts) {
		Tex cloth = sewingArt("cloth", SewingFont.PITCH, SewingFont.PITCH);
		Tex needle = sewingArt("needle", SewingFont.NEEDLE_LENGTH, SewingFont.NEEDLE_WIDTH);
		Tex thread = sewingArt("thread", SewingFont.DOT, SewingFont.DOT);
		Tex stitchIn = sewingArt("stitch_in", SewingFont.MARK, SewingFont.MARK);
		Tex stitchOut = sewingArt("stitch_out", SewingFont.MARK, SewingFont.MARK);
		Tex hole = sewingArt("hole", SewingFont.MARK, SewingFont.MARK);
		Tex band = sewingArt("band", SewingFont.cellWidth(SewingFont.BAND), SewingFont.PITCH);

		Map<String, Tex> textures = new LinkedHashMap<>();
		for (Chapter chapter : Chapter.values()) {
			textures.put(SewingFont.cloth(chapter).name(), cloth.colourised(chapterColours.get(chapter)));
		}
		JsonObject outlines = new JsonObject();
		for (Patches.Patch patch : Patches.all()) {
			Tex art = arts.get(patch.id());
			textures.put(SewingFont.patch(patch).name(), art.scale(Seam.scale(patch)));
			JsonArray segments = new JsonArray();
			for (int[] s : outline(art, patch.id())) segments.add(J.nums(s[0], s[1], s[2], s[3], s[4], s[5]));
			outlines.add(patch.id(), segments);
		}
		textures.put(SewingFont.THREAD.name(), thread);
		textures.put(SewingFont.STITCH_IN.name(), stitchIn);
		textures.put(SewingFont.STITCH_OUT.name(), stitchOut);
		textures.put(SewingFont.HOLE.name(), hole);
		textures.put(SewingFont.NEEDLE_R.name(), needle);
		textures.put(SewingFont.NEEDLE_L.name(), needle.flipX());
		Tex down = needle.rotated();
		textures.put(SewingFont.NEEDLE_D.name(), down);
		textures.put(SewingFont.NEEDLE_U.name(), down.flipY());
		Tex ascii = Vanilla.texture("font/ascii");
		String cut = "Cut the thread";
		int textWidth = Tex.textWidth(ascii, cut), textX = (band.width - textWidth) / 2;
		for (int y = 4; y <= 16; y++) band = band.line(textX - 3, y, textWidth + 6, BAND_FLAT);
		textures.put(SewingFont.BAND_GLYPH.name(), band.stampText(ascii, cut, textX, (SewingFont.PITCH - 8) / 2, BAND_TEXT, false));

		List<JsonObject> providers = new ArrayList<>();
		for (SewingFont.Glyph glyph : SewingFont.glyphs()) {
			Tex tex = textures.get(glyph.name());
			require(tex != null, "no texture for sewing glyph " + glyph.name());
			require(tex.width == glyph.width() && tex.height == glyph.height(),
					"sewing glyph " + glyph.name() + " is " + tex.width + "\u00d7" + tex.height + ", the font expects " + glyph.width() + "\u00d7" + glyph.height());
			String file = MOD + ":" + SewingFont.TEXTURE_DIR + glyph.name() + ".png";
			// Padded below to the tallest ascent it is drawn with: the client rejects an ascent above the height.
			png(assets.resolve("textures/" + SewingFont.TEXTURE_DIR + glyph.name() + ".png"), tex.padBottom(glyph.textureHeight()).reachingRightEdge());
			for (int top = glyph.minTop(); top <= glyph.maxTop(); top++) {
				int ascent = SewingFont.Glyph.ascent(top);
				require(ascent >= 0 && ascent <= glyph.textureHeight(), "sewing glyph " + glyph.name() + " at top " + top + " needs ascent " + ascent);
				providers.add(obj("type", "bitmap", "file", file, "height", glyph.textureHeight(), "ascent", ascent,
						"chars", arr(String.valueOf(glyph.at(top)))));
			}
		}
		JsonObject advances = new JsonObject();
		SewingFont.spaceAdvances().forEach((c, advance) -> advances.addProperty(String.valueOf(c), advance));
		providers.add(obj("type", "space", "advances", advances));
		json(assets.resolve("font/" + SewingFont.ID.getPath() + ".json"), obj("providers", arr(providers.toArray())));
		json(root.resolve(Outline.RESOURCE.substring(1)), outlines);
		Ovvar.LOGGER.info("[{} datagen] sewing font: {} glyphs, {} codepoints; {} outlines", MOD, textures.size(), providers.size() - 1, outlines.size());
	}

	private static Tex sewingArt(String name, int width, int height) {
		Tex tex = art("sewing/" + name);
		require(tex.width == width && tex.height == height, "sewing/" + name + ".png must be " + width + "\u00d7" + height);
		return tex;
	}

	/**
	 * The outline of the art's opaque texels for {@link Outline}: unit segments
	 * {@code {x0, y0, x1, y1, nx, ny}} along the boundary, chained clockwise from the top-left
	 * corner, the normal pointing off the art. Each opaque texel contributes its sides that face
	 * a transparent texel or the edge; following them end to start closes the loops, and the
	 * longest loop is the outer edge (the art's holes, if any, are not sewn around).
	 */
	static List<int[]> outline(Tex art, String name) {
		List<int[]> edges = new ArrayList<>();
		for (int y = 0; y < art.height; y++) {
			for (int x = 0; x < art.width; x++) {
				if (!opaque(art, x, y)) continue;
				if (!opaque(art, x, y - 1)) edges.add(new int[]{x, y, x + 1, y, 0, -1});
				if (!opaque(art, x + 1, y)) edges.add(new int[]{x + 1, y, x + 1, y + 1, 1, 0});
				if (!opaque(art, x, y + 1)) edges.add(new int[]{x + 1, y + 1, x, y + 1, 0, 1});
				if (!opaque(art, x - 1, y)) edges.add(new int[]{x, y + 1, x, y, -1, 0});
			}
		}
		require(!edges.isEmpty(), "patches/" + name + ".png has no opaque texels to sew around");
		List<int[]> best = List.of();
		boolean[] used = new boolean[edges.size()];
		for (int i = 0; i < edges.size(); i++) {
			if (used[i]) continue;
			List<int[]> loop = new ArrayList<>();
			int current = i;
			while (current >= 0) {
				used[current] = true;
				int[] e = edges.get(current);
				loop.add(e);
				current = -1;
				for (int j = 0; j < edges.size(); j++) {
					int[] f = edges.get(j);
					if (!used[j] && f[0] == e[2] && f[1] == e[3]) { current = j; break; }
				}
			}
			if (loop.size() > best.size()) best = loop;
		}
		int start = 0;
		for (int i = 1; i < best.size(); i++) {
			int[] a = best.get(i), b = best.get(start);
			if (a[1] < b[1] || (a[1] == b[1] && a[0] < b[0])) start = i;
		}
		List<int[]> out = new ArrayList<>(best.subList(start, best.size()));
		out.addAll(best.subList(0, start));
		return out;
	}

	private static boolean opaque(Tex art, int x, int y) {
		return x >= 0 && y >= 0 && x < art.width && y < art.height && Tex.a(art.get(x, y)) >= 128;
	}

	/** A patch's inventory icon: its art scaled to fill 16 px, centred. */
	private static Tex icon(Tex art) {
		int scale = Math.max(1, 16 / Math.max(art.width, art.height));
		Tex big = art.scale(scale);
		return Tex.blank(16, 16).blit(big, 0, 0, big.width, big.height, (16 - big.width) / 2, (16 - big.height) / 2);
	}

	/** The equipment definition of one half with nothing sewn on; Combos writes the others into the pack. */
	private void equipment(Chapter chapter, Piece piece, boolean nercabbad) {
		json(assets.resolve("equipment/" + Looks.assetPath(chapter, piece, nercabbad, "") + ".json"),
				JsonParser.parseString(EquipmentJson.json(chapter, piece, nercabbad, List.of())));
	}

	/**
	 * Our textures carry the marker texel the core shader looks for and, two left of it, the
	 * layer texel: R = 2 × the armour model's inflation for the piece's layer (see ovvar.glsl).
	 */
	private static Tex marked(Tex tex, Piece piece) {
		require(tex.width == W && tex.height == H, "a garment texture is " + tex.width + "×" + tex.height + ", not " + W + "×" + H);
		require(tex.get(MARKER_X, MARKER_Y) == 0 && tex.get(LAYER_X, MARKER_Y) == 0, "a garment texture draws on the marker texels");
		return tex.with(MARKER_X, MARKER_Y, MARKER).with(LAYER_X, MARKER_Y, rgb((int) Math.round(2 * Spot.inflate(piece)), 0, 0));
	}

	/**
	 * Art on a garment texture at texel column {@code x} (its top-left; the cell's row, centred
	 * vertically), clipped to the part's side rows — a big patch hangs over its neighbours, never
	 * off its part. The part's strip is a loop round the box, so what hangs off either end of it
	 * comes round to the other end (past the outer face of a limb lies its back face).
	 */
	private static Tex placed(Spot spot, Tex art, int x) {
		int y = spot.v * D + (Spot.PX - art.height) / 2;
		int stripStart = Spot.stripStart(spot) * D, stripWidth = Spot.stripWidth(spot) * D;
		require(art.width <= stripWidth, "patch art is wider than the " + spot.id() + " cell's part");
		Tex out = Tex.blank(W, H);
		boolean any = false;
		for (int ax = 0; ax < art.width; ax++) {
			int column = stripStart + Math.floorMod(x + ax - stripStart, stripWidth);
			for (int row = Math.max(y, 20 * D); row < Math.min(y + art.height, 32 * D); row++) {
				int p = art.get(ax, row - y);
				if (p != 0) { out = out.with(column, row, p); any = true; }
			}
		}
		require(any, "patch art lands entirely off the " + spot.id() + " cell's part");
		return out;
	}

	/**
	 * The same, but as vanilla will draw it from a trim texture: the strip wrapped around the
	 * box the way the shader does for a placement ({@link Spot#anchored}), baked texel by texel
	 * — each column of the part's side rows shows the art column the shader would sample there.
	 */
	private static Tex placedWrapped(Spot spot, Tex art, int x) {
		Tex flat = placed(spot, art, x);   // the art on the strip, wrapped round it
		int stripStart = Spot.stripStart(spot) * D, stripEnd = stripStart + Spot.stripWidth(spot) * D;
		double inflate = Spot.inflate(spot.piece);
		int anchor = Spot.face(spot);
		Tex out = Tex.blank(W, H);
		for (int column = stripStart; column < stripEnd; column++) {
			double w = Spot.anchored((column + 0.5) / D, inflate, anchor);
			if (w < 0) continue;
			int texel = stripStart + (int) Math.floor(w * D);
			for (int row = 20 * D; row < 32 * D; row++) {
				int p = flat.get(texel, row);
				if (p != 0) out = out.with(column, row, p);
			}
		}
		return out;
	}

	/** A placement texture: drawn on one side of the model only (both, for body cells), continuous round the box from the cell's face. */
	private static Tex sided(Tex tex, Spot spot, Spot.Side side) {
		return marked(tex.with(MARKER_KIND_X, MARKER_Y, rgb(KIND_SIDED, side.ordinal(), Spot.face(spot))), spot.piece);
	}

	// ---- the texel contract with ovvar.glsl

	/** Left of the marker: R = kind; sided: G = side, B = the face of its strip; preview: G = cells in the half, B = instant designs. Base textures have none (0). */
	private static final int MARKER_KIND_X = W - 2, KIND_SIDED = 1, KIND_PREVIEW = 2;
	/** Two left of the marker: R = 2 × the model inflation of the layer the texture is for (the squeeze needs it). */
	private static final int LAYER_X = W - 3;
	/** Always transparent in a patch texture: what the shader draws where there is nothing. */
	private static final int BLANK_X = W - 1, BLANK_Y = H / 2 - 2;
	/**
	 * Preview texture tables, column-major 16 tall, {@value #TABLE_COLUMNS} columns each: cell
	 * index (in the half) → (u, v, side); design index → (library x, y, cells) and, {@value
	 * #TABLE_COLUMNS} columns further right, (art width, art height) — positions in texels of
	 * this texture.
	 */
	private static final int CELL_TABLE_X = 40 * D, PATCH_TABLE_X = 44 * D, TABLE_COLUMNS = 2 * D, TABLE_SIZE = 16 * TABLE_COLUMNS;
	/**
	 * Preview library: the head rows (skin texels 0..64 × 0..16) as a grid of cells, minus the
	 * tables' columns (40..48) and the cell holding the marker row's texels (60..64 × 12..16).
	 * A design takes a block of cells its art's size.
	 */
	private static final int LIBRARY_COLUMNS = 16, LIBRARY_ROWS = 4;

	private static boolean libraryFree(int cx, int cy) {
		return !(cx >= 10 && cx < 12) && !(cx == 15 && cy == 3);
	}

	/** First-fit block of w×h cells in the library; returns its top-left in skin texels. */
	private static int[] libraryBlock(boolean[][] taken, int w, int h, String id) {
		for (int cy = 0; cy + h <= LIBRARY_ROWS; cy++) {
			for (int cx = 0; cx + w <= LIBRARY_COLUMNS; cx++) {
				boolean free = true;
				for (int x = cx; x < cx + w && free; x++) for (int y = cy; y < cy + h; y++) if (taken[x][y] || !libraryFree(x, y)) { free = false; break; }
				if (!free) continue;
				for (int x = cx; x < cx + w; x++) for (int y = cy; y < cy + h; y++) taken[x][y] = true;
				return new int[]{cx * Spot.SIZE, cy * Spot.SIZE};
			}
		}
		throw new IllegalStateException("[" + MOD + " datagen] the preview library is full: no room for " + id + " (" + w + "×" + h + " cells)");
	}

	/**
	 * The left limb's art one strip up from the right limb's box, with every face mirrored in
	 * place: the model draws the left limb as a mirror image off the right strips, and the shader
	 * sends those fragments here, so the art must be pre-mirrored to come out straight. The art is
	 * the skin's own left limb (flattened with its second layer); a skin without one gets a copy of
	 * the right limb. Box layout: top and bottom faces (4×4) at +4 and +8 on the first four rows,
	 * then four 4×12 side faces.
	 */
	private static Tex withLeft(Tex tex, int[] box, Tex skin, int[] leftBox) {
		int x = box[0], y = box[1], my = y - Spot.MIRROR_SHIFT;
		Tex left = Tex.blank(64, 64).blit(skin, leftBox[0], leftBox[1], leftBox[2], leftBox[3], 0, 0);
		Tex out;
		if (left.isEmpty()) {
			out = tex.blit(tex, x, y, box[2], box[3], x, my);
		} else {
			// The skin lays the left limb out for an unmirrored cube: its first side strip is the
			// inner face and its third the outer, the other way round from the right limb's strips
			// the model reads. Swap them so the outer art lands on the outer face.
			out = tex.blit(skin, leftBox[0], leftBox[1], leftBox[2], leftBox[3], x, my)
					.blit(skin, leftBox[0] + 8, leftBox[1] + 4, 4, 12, x, my + 4)
					.blit(skin, leftBox[0], leftBox[1] + 4, 4, 12, x + 8, my + 4);
		}
		out = out.flipX(x + 4, my, 4, 4).flipX(x + 8, my, 4, 4);
		for (int face = 0; face < 4; face++) out = out.flipX(x + face * 4, my + 4, 4, 12);
		return out;
	}

	/**
	 * The website renders the skin's second layer as a raised 3D layer — belt folds, pockets, the
	 * hanging top of a rolled-down ovve. The armour model has one box per part, so that layer is
	 * painted onto the base boxes (and onto the left limbs' own boxes for {@link #withLeft}).
	 */
	private static Tex flattened(Tex skin) {
		Tex out = skin;
		int[][][] pairs = {{BODY, BODY_OUTER}, {RIGHT_ARM, RIGHT_ARM_OUTER}, {RIGHT_LEG, RIGHT_LEG_OUTER}, {LEFT_ARM, LEFT_ARM_OUTER}, {LEFT_LEG, LEFT_LEG_OUTER}};
		for (int[][] pair : pairs) {
			int[] base = pair[0], outer = pair[1];
			Tex over = Tex.blank(64, 64).blit(skin, outer[0], outer[1], outer[2], outer[3], base[0], base[1]);
			out = out.composite(over);
		}
		return out;
	}

	private static Tex art(String name) {
		return Tex.read(Vanilla.open("art/" + MOD + "/" + name + ".png"));
	}

	private static Tex overlay(String name, Chapter chapter) {
		Tex tex = art(name);
		require(tex.width == 64 && tex.height == 64, name + ".png is not a 64×64 skin overlay");
		tex = tex.withoutGreenKey();
		tex = chapter.tint == null ? tex : tex.tinted(chapter.tint);
		return flattened(tex);
	}

	private void layer(Chapter chapter, Piece piece, String name, Tex tex) {
		tex = marked(tex, piece);
		png(assets.resolve("textures/entity/equipment/" + piece.layer + "/" + chapter.id + "/" + name + ".png"), tex);
	}

	/** Item definition, flat model and texture for one item. */
	private void item(String name, Tex texture) {
		require(texture.width == 16 && texture.height == 16, name + " icon is not 16×16");
		json(assets.resolve("items/" + name + ".json"), J.itemDef(MOD + ":item/" + name));
		json(assets.resolve("models/item/" + name + ".json"),
				obj("parent", "minecraft:item/generated", "textures", obj("layer0", MOD + ":item/" + name)));
		png(assets.resolve("textures/item/" + name + ".png"), texture);
	}

	/**
	 * An item whose model is one flat, unlit quad of the texture — no thickness, no sides — for
	 * the stand displays. The quad faces +z in model space; an item display turns it 180° about
	 * y, so the readable side faces the display's -z.
	 */
	private void sprite(String name, Tex texture) {
		require(texture.width == 16 && texture.height == 16, name + " sprite is not 16×16");
		json(assets.resolve("items/" + name + ".json"), J.itemDef(MOD + ":item/" + name));
		json(assets.resolve("models/item/" + name + ".json"), obj(
				"textures", obj("0", MOD + ":item/" + name, "particle", MOD + ":item/" + name),
				"elements", arr(obj(
						"from", arr(0, 0, 8), "to", arr(16, 16, 8), "shade", false,
						"faces", obj("south", obj("uv", arr(0, 0, 16, 16), "texture", "#0"))))));
		png(assets.resolve("textures/item/" + name + ".png"), texture);
	}

	private static void require(boolean ok, String message) {
		if (!ok) throw new IllegalStateException("[" + MOD + " datagen] " + message);
	}

	private void json(Path path, JsonElement element) {
		writes.add(DataProvider.saveStable(out, element, path));
	}

	private void png(Path path, Tex tex) {
		byte[] data = tex.png();
		writes.add(CompletableFuture.runAsync(() -> {
			try {
				out.writeIfNeeded(path, data, Hashing.sha1().hashBytes(data));
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}));
	}
}
