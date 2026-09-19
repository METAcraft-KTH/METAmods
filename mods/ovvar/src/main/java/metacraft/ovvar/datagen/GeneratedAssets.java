package metacraft.ovvar.datagen;

import com.google.gson.JsonArray;
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
import metacraft.ovvar.sewing.WardrobeAction;
import metacraft.ovvar.sewing.Seam;
import metacraft.ovvar.sewing.SewingFont;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
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

	/**
	 * Skin-layout boxes (x, y, w, h) that the armour model draws, and which garment owns each.
	 *
	 * <p>These and the texel contract below are public because {@link BlockbenchManifest} publishes
	 * them for the Blockbench plugin and a game test in {@code metacraft.ovvar.gametest} holds the
	 * manifest to them — quoting the generator's own numbers rather than keeping a second copy is
	 * the whole point, so they have to be readable from outside this package.
	 */
	public static final int[] BODY = {16, 16, 24, 16};
	public static final int[] RIGHT_ARM = {40, 16, 16, 16};
	public static final int[] RIGHT_LEG = {0, 16, 16, 16};
	/** The skin's second layer for each, and the left limbs (base, second layer) — the website draws these in 3D. */
	public static final int[] BODY_OUTER = {16, 32, 24, 16};
	public static final int[] RIGHT_ARM_OUTER = {40, 32, 16, 16}, RIGHT_LEG_OUTER = {0, 32, 16, 16};
	public static final int[] LEFT_ARM = {32, 48, 16, 16}, LEFT_ARM_OUTER = {48, 48, 16, 16};
	public static final int[] LEFT_LEG = {16, 48, 16, 16}, LEFT_LEG_OUTER = {0, 48, 16, 16};
	/** The trousers' share of the body box: the bottom two texel rows of its side faces (the waistband). */
	public static final int[] WAIST = {16, 30, 24, 2};
	/**
	 * Garment and patch textures are the armour layout at {@link Spot#DETAIL} texels per skin
	 * texel ({@code W}×{@code H}); base garments cut from the skins are upscaled to it, patch art
	 * is drawn at it. Every texel position the shader knows (marker, tables, library) scales with it.
	 */
	private static final int D = Spot.DETAIL, W = 64 * D, H = 32 * D;
	/** The texel our core shader checks before treating a texture as ours: magenta at alpha 2. */
	public static final int MARKER_X = W - 1, MARKER_Y = H / 2 - 1, MARKER = 0x02FF00FF;

	private final Path root, assets, data;
	/** This run's files; made fresh in {@link #run}, which is where the cache to write them through arrives. */
	private Writes files;

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
		this.files = new Writes(output);
		Map<String, String> lang = new LinkedHashMap<>();
		lang.put("itemGroup." + MOD, "Ovvar");
		// The companion top's inventory icon: the vanilla empty-chestplate-slot silhouette, so the slot
		// reads as an ordinary empty chest slot while the ovve's sleeves still render from its
		// equipment asset (which the inventory model does not touch).
		files.json(assets.resolve("items/blank.json"), obj("model", obj("type", "minecraft:model", "model", MOD + ":item/empty_chest")));
		files.json(assets.resolve("models/item/empty_chest.json"),
				obj("parent", "minecraft:item/generated", "textures", obj("layer0", MOD + ":item/empty_chest")));
		// The empty-slot silhouette is a GUI sprite, a different atlas than item textures, so copy it
		// into one of ours for the icon to resolve.
		files.png(assets.resolve("textures/item/empty_chest.png"), Vanilla.texture("gui/sprites/container/slot/chestplate"));
		// A model that draws nothing at all: the wardrobe screen's preview slots must keep their
		// tooltip (which patch is on which spot) while the paper doll behind them shows through, so
		// their item wears this ovvar:invisible model instead of an icon.
		item("invisible", Tex.blank(16, 16));

		// The wardrobe's own action icons, and a dimmed twin of each for an action this server does
		// not allow — the same icon, plainly out of use, rather than a pane of grey glass.
		List<String> icons = new ArrayList<>();
		for (WardrobeAction action : WardrobeAction.values()) {
			if (icons.contains(action.art())) continue;   // the page arrows share the rotation arrows' art
			icons.add(action.art());
			Tex art = art(action.art());
			require(art.width == 16 && art.height == 16, action.art() + ".png is " + art.width + "×" + art.height + ", not 16×16");
			item(action.itemName(true), art);
			item(action.itemName(false), dimmed(art));
		}
		Tex icon = art("icon");
		require(icon.width == 16 && icon.height == 16, "icon.png is not 16×16");

		// Patches: every art a patch has, an icon, an item. A patch may be drawn at more than one size
		// and have smaller ones scaled down from its 16 px art (Patches.Art), so the art is loaded per
		// variant — Tex.art knows which of the two a variant is — and every consumer below asks
		// Patches.artFor which one its place shows.
		Map<Patches.Art, Tex> arts = new LinkedHashMap<>();
		for (Patches.Patch patch : Patches.all()) {
			for (Patches.Art variant : patch.variants()) {
				Tex art = Tex.art(variant);
				require(art.width == variant.width() && art.height == variant.height(),
						variant.file() + ".png is " + art.width + "×" + art.height + ", its name says "
								+ variant.width() + "×" + variant.height()
								+ (variant.byDefault() ? " (the size the catalogue declares)" : ""));
				arts.put(variant, art);
			}
			String name = ModContent.patchId(patch).getPath();
			item(name, icon(arts.get(Patches.iconArt(patch))));
			// Flat pieces for the stand displays: the art 1:1 in the sprite's centre, one model per
			// piece any cell cuts it into (the whole art included) — of the art that cell shows, so
			// a shoulder's sprite is the variant that fits it, like everything else drawn there.
			record Flat(Patches.Art art, PatchPieces.Piece piece) {}
			Map<String, Flat> pieces = new LinkedHashMap<>();
			for (Spot spot : Spot.values()) {
				if (!patch.fits(spot)) continue;
				Patches.Art variant = Patches.artFor(patch, spot);
				for (PatchPieces.Piece piece : PatchPieces.of(spot, variant)) {
					pieces.putIfAbsent(PatchItem.flatKey(variant, piece, false), new Flat(variant, piece));
				}
			}
			for (Flat flat : pieces.values()) {
				Patches.Art variant = flat.art();
				PatchPieces.Piece piece = flat.piece();
				Tex art = arts.get(variant);
				int ox = (16 - art.width) / 2, oy = (16 - art.height) / 2;
				Tex sprite = Tex.blank(16, 16).blit(art, piece.x0(), piece.y0(), piece.x1() - piece.x0(), piece.y1() - piece.y0(), ox + piece.x0(), oy + piece.y0());
				sprite(PatchItem.flatModel(name, variant, piece, false), sprite);
				sprite(PatchItem.flatModel(name, variant, piece, true), ghosted(sprite));   // the preview: washed out, "not sewn yet"
			}
			lang.put("item." + MOD + "." + name, patch.name() + " patch");
		}
		// A variant needs no catalogue entry, so a misspelt size in a file name is otherwise silent —
		// the art is simply never drawn. This is where an artist finds out: a file that begins with a
		// patch's id and an underscore is meant to be a variant of it, so it must be one this build
		// can ask for (Patches discovers them by probing those names). A file naming no patch at all
		// is art nobody uses, which is a note rather than a failure.
		for (String file : Vanilla.dir("art/" + MOD + "/patches")) {
			if (!file.endsWith(".png")) continue;
			String named = file.substring(0, file.length() - 4);
			boolean known = false, variantOf = false;
			for (Patches.Patch patch : Patches.all()) {
				// Against the arts that are files: a generated one borrows a variant's name but ships
				// nothing, so a file of that name is a drawing and stops the generating instead.
				for (Patches.Art variant : patch.variants()) known |= !variant.generated() && variant.file().equals("patches/" + named);
				variantOf |= named.startsWith(patch.id() + "_");
			}
			require(known || !variantOf, "art/" + MOD + "/patches/" + file + " reads as a variant of a patch but is not"
					+ " one this build can ask for — a variant is named <id>_<w>x<h>.png, with an even w and h up to "
					+ Patches.MAX_ART + " (and a seat patch's variants are " + 2 * Spot.PX + " px wide)");
			if (!known) Ovvar.LOGGER.info("[{} datagen] art/{}/patches/{} is in no catalogue entry; nothing draws it", MOD, MOD, file);
		}

		// One static texture per (cell, patch): the art on its cell, drawn on one side only by the
		// shader (marker texel: kind 1, side). The seat is two of them, one per leg.
		int placementTextures = 0;
		for (Spot spot : Spot.values()) {
			for (Patches.Patch patch : Patches.all()) {
				if (!patch.fits(spot)) continue;
				// The art this cell shows, which need not be the catalogue's own size: a cell the art
				// is clipped to takes a variant that fits it whole, a cell as big as art may get takes
				// the largest one (Patches.artFor).
				Patches.Art variant = Patches.artFor(patch, spot);
				Tex art = arts.get(variant);
				String dir = "textures/entity/equipment/" + spot.piece.layer + "/";
				if (spot == Spot.SEAT) {
					// The art is drawn as seen from behind, so its left half sits on the wearer's LEFT leg
					// (the viewer's left when looking at the seat) and the right half on the right leg:
					// Spot.seatColumn is that convention and Spot.seatHalf the cut it makes. Each half is
					// then placed like any other art — centred on the cell's row, so a seat patch taller
					// than the row hangs onto the cloth below it, and clipped to the part's side rows.
					for (Spot.Side side : new Spot.Side[]{Spot.Side.RIGHT, Spot.Side.LEFT}) {
						Tex half = art.crop(Spot.seatHalf(side), 0, Spot.PX, art.height);
						if (side == Spot.Side.LEFT) half = half.flipX();   // the model mirrors the left leg
						Tex tex = placed(spot, half, spot.u * D);
						String suffix = side == Spot.Side.LEFT ? "_l" : "_r";
						files.png(assets.resolve(dir + "patch/seat/" + patch.id() + suffix + ".png"), sided(tex, spot, side));
					}
					placementTextures += 2;
					continue;
				}
				// Centred on the cell, hanging over it if bigger, clipped to the part's side rows;
				// a left cell's art is mirrored (the model mirrors the left limb).
				Tex placed = placed(spot, spot.side == Spot.Side.LEFT ? art.flipX() : art, spot.u * D + variant.offsetX(spot));
				files.png(assets.resolve(dir + "patch/" + spot.id() + "/" + patch.id() + ".png"), sided(placed, spot, spot.side));
				placementTextures++;
			}
		}

		// The trim channel: one trim pattern per (body cell, patch), the top's fourth instant patch.
		// Vanilla draws trims, so the squeeze to square pixels (ovvar.glsl) is baked in here, to the
		// texel — tolerable on the 16-pixel-wide chest and back faces. Body cells only: a trim
		// carries no marker texel, so our shader cannot hide one on the other limb (Trims.fits).
		List<String> trimTextures = new ArrayList<>();
		for (Spot spot : Spot.values()) {
			for (Patches.Patch patch : Patches.all()) {
				Placement placement = spot == Spot.SEAT || !patch.fits(spot) ? null : new Placement(spot, patch);
				if (placement == null || !Trims.fits(placement)) continue;
				String name = Trims.patternName(placement);
				Patches.Art variant = Patches.artFor(patch, spot);
				Tex tex = placedWrapped(spot, arts.get(variant), spot.u * D + variant.offsetX(spot));
				files.png(assets.resolve("textures/trims/entity/" + spot.piece.layer + "/" + name + ".png"), tex);
				trimTextures.add(MOD + ":trims/entity/" + spot.piece.layer + "/" + name);
				files.json(data.resolve("trim_pattern/" + name + ".json"),
						obj("asset_id", MOD + ":" + name, "decal", false, "description", obj("text", patch.name() + " on the " + spot.label())));
			}
		}
		// The material is a colour permutation of a key palette onto itself, so the key is every colour any patch uses.
		List<Integer> colours = new ArrayList<>();
		for (Tex art : arts.values()) for (int c : art.opaqueColours()) if (!colours.contains(c)) colours.add(c);
		Tex key = Tex.blank(colours.size(), 1);
		for (int i = 0; i < colours.size(); i++) key = key.with(i, 0, colours.get(i));
		String palettes = "textures/trims/color_palettes/";
		files.png(assets.resolve(palettes + "key.png"), key);
		files.png(assets.resolve(palettes + Trims.MATERIAL + ".png"), key);
		files.json(data.resolve("trim_material/" + Trims.MATERIAL + ".json"), obj("palette_id", MOD + ":" + Trims.MATERIAL, "description", obj("text", "Patch")));   // 26.3: an id, the permutation key
		files.json(assets.getParent().resolve("minecraft/atlases/armor_trims.json"), obj("sources", arr(obj(
				"type", "minecraft:paletted_permutations",
				"textures", arr(trimTextures.toArray()),
				"palette_key", MOD + ":trims/color_palettes/key",
				"permutations", obj(Trims.MATERIAL, MOD + ":trims/color_palettes/" + Trims.MATERIAL)))));
		Ovvar.LOGGER.info("[{} datagen] {} trim patterns", MOD, trimTextures.size());

		// The preview layer per half: every instant design's art in the library (a block of cells
		// its size), the cell and design tables, marker kind 2. The shader draws what the dye
		// colour's slots name.
		// One block per art a design can be drawn as, which is one per Patches.Fit: the instant
		// channel carries a design, not a cell's choice of its PNGs, so the fits' arts all have to be
		// in the library and the design table says where each of them is. A patch whose fits pick the
		// same PNG gets one block, shared. Allocated biggest-block-first: first-fit in catalogue order
		// leaves the small arts' gaps scattered between the big ones, so a late 2×2 can find no hole
		// even though the cells for it exist.
		Map<Patches.Art, int[]> library = new LinkedHashMap<>();
		boolean[][] taken = new boolean[LIBRARY_COLUMNS][LIBRARY_ROWS];
		List<Patches.Art> catalogue = new ArrayList<>();
		for (Patches.Patch patch : Patches.all()) {
			if (Patches.code(patch) > Looks.INSTANT_DESIGNS) continue;   // never in the dye colour: no library entry
			for (Patches.Fit fit : Patches.Fit.values()) {
				Patches.Art variant = Patches.artFor(patch, fit);
				if (!catalogue.contains(variant)) catalogue.add(variant);
			}
		}
		catalogue.sort(Comparator.<Patches.Art>comparingInt(v -> v.cells() * ((v.height() + Spot.PX - 1) / Spot.PX)).reversed());
		for (Patches.Art variant : catalogue) {
			library.put(variant, libraryBlock(taken, variant.cells(), (variant.height() + Spot.PX - 1) / Spot.PX, variant.file()));
		}
		// The legs' preview is also drawn by the boots pass (the outer model, inflate 1), as the
		// second dye channel: the same texture with that layer's texel, in the humanoid folder.
		record PreviewTarget(Piece piece, String layer, String name, Piece inflateAs) {}
		List<PreviewTarget> previews = new ArrayList<>();
		for (Piece piece : Piece.values()) previews.add(new PreviewTarget(piece, piece.layer, EquipmentJson.previewTexture(piece), piece));
		previews.add(new PreviewTarget(Piece.BOTTOM, Piece.TOP.layer, EquipmentJson.FEET_PREVIEW, Piece.TOP));
		for (PreviewTarget target : previews) {
			List<Spot> cells = Spot.cells(target.piece);
			require(cells.size() <= TABLE_SIZE, "the preview tables hold " + TABLE_SIZE + " cells, not " + cells.size());
			require(Looks.INSTANT_DESIGNS <= FIT_SLOTS, "the design table holds " + FIT_SLOTS + " designs per fit, not " + Looks.INSTANT_DESIGNS);
			Tex tex = Tex.blank(W, H).with(MARKER_KIND_X, MARKER_Y, rgb(KIND_PREVIEW, cells.size(), Looks.INSTANT_DESIGNS));
			for (Patches.Patch patch : Patches.all()) {
				if (Patches.code(patch) > Looks.INSTANT_DESIGNS) continue;
				int design = Patches.code(patch) - 1;
				for (Patches.Fit fit : Patches.Fit.values()) {
					Patches.Art variant = Patches.artFor(patch, fit);
					int[] at = library.get(variant);
					Tex art = arts.get(variant);
					tex = tex.blit(art, 0, 0, art.width, art.height, at[0] * D, at[1] * D);
					// The design table, a row per (design, fit): where that fit's art is in the library,
					// and its size beside it. The fit is the shader's own reading of the cell it is
					// drawing (ovvar.glsl: OVVAR_FIT_*), so the two must key this the same way.
					int slot = design + fit.ordinal() * FIT_SLOTS;
					tex = tex.with(PATCH_TABLE_X + slot / 16, slot % 16, rgb(at[0] * D, at[1] * D, variant.cells()));
					tex = tex.with(PATCH_SIZE_TABLE_X + slot / 16, slot % 16, rgb(art.width, art.height, 0));
				}
			}
			for (int index = 0; index < cells.size(); index++) {
				Spot spot = cells.get(index);
				tex = tex.with(CELL_TABLE_X + index / 16, index % 16, rgb(spot.u * D, spot.v * D, spot.side.ordinal()));
				// A cell is not one size any more (Spot.BACK_BIG is two cells each way, the seat two
				// wide), and the shader centres the art in it, so the size travels beside it.
				// The cell's v is also how the shader tells a top-face cell (the shoulders) from a
				// side one: a row above Spot.FACE_ROW is the box's top face, where there is no
				// squeeze round the box and the art is clipped to the face.
				// B is Spot.layer: cells overlap (the big back cell under the two back-top ones) and
				// the ranked set in the dye colour has no order of its own, so the shader draws the
				// highest layer of the cells a fragment falls in. The pack stacks its layers in the
				// same order (EquipmentJson.layerTextures).
				tex = tex.with(CELL_SIZE_TABLE_X + index / 16, index % 16, rgb(spot.px(), spot.pxHeight(), spot.layer()));
			}
			require(tex.get(BLANK_X, BLANK_Y) == 0, "the preview texture draws on the blank texel");
			files.png(assets.resolve("textures/entity/equipment/" + target.layer + "/" + target.name + ".png"), marked(tex, target.inflateAs));
		}
		for (String material : OvveFeet.MATERIALS) {
			require(Vanilla.exists("assets/minecraft/textures/entity/equipment/humanoid/" + material + ".png"), "no vanilla equipment texture for " + material);
		}
		for (String material : concat(OvveFeet.NONE, OvveFeet.MATERIALS)) {
			files.json(assets.resolve("equipment/feet/" + material + ".json"), JsonParser.parseString(EquipmentJson.feetJson(material)));
		}
		Ovvar.LOGGER.info("[{} datagen] {} placement textures, {} arts in the preview library", MOD, placementTextures, library.size());

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

			// The chest wrap: the top under a real chestplate of each metal material. The chestplate's
			// arms and neck are largely transparent, so the ovve's sleeves show through underneath.
			for (String material : OvveFeet.MATERIALS) {
				files.json(assets.resolve("equipment/chest/" + chapter.id + "/" + material + ".json"),
						JsonParser.parseString(EquipmentJson.chestJson(chapter, material)));
			}

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
		files.json(assets.resolve("lang/en_us.json"), langJson);
		return files.allOf();
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
	 *
	 * <p>The sewing game is about the patch rather than about where it is going — it is played
	 * before the cell is committed to — so it shows the catalogue's own art ({@code Patch.art()}),
	 * whatever the cell it lands on will pick ({@link Patches#artFor}).
	 */
	private void sewingFont(Map<Chapter, Integer> chapterColours, Map<Patches.Art, Tex> arts) {
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
			Tex art = arts.get(patch.art());
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
			files.png(assets.resolve("textures/" + SewingFont.TEXTURE_DIR + glyph.name() + ".png"), tex.padBottom(glyph.textureHeight()).reachingRightEdge());
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
		files.json(assets.resolve("font/" + SewingFont.ID.getPath() + ".json"), obj("providers", arr(providers.toArray())));
		files.json(root.resolve(Outline.RESOURCE.substring(1)), outlines);
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
		files.json(assets.resolve("equipment/" + Looks.assetPath(chapter, piece, nercabbad, "") + ".json"),
				JsonParser.parseString(EquipmentJson.json(chapter, piece, nercabbad, List.of())));
	}

	/**
	 * Our textures carry the marker texel the core shader looks for and, two left of it, the
	 * layer texel: R = 2 × the armour model's inflation for the piece's layer (see ovvar.glsl).
	 */
	private static Tex marked(Tex tex, Piece piece) {
		require(tex.width == W && tex.height == H, "a garment texture is " + tex.width + "×" + tex.height + ", not " + W + "×" + H);
		require(tex.get(MARKER_X, MARKER_Y) == 0 && tex.get(LAYER_X, MARKER_Y) == 0, "a garment texture draws on the marker texels");
		for (int i = 0; i < DEBUG.length; i++) {
			require(tex.get(DEBUG_X + i, MARKER_Y) == 0, "a garment texture draws on the debug palette texels");
		}
		Tex out = tex.with(MARKER_X, MARKER_Y, MARKER).with(LAYER_X, MARKER_Y, rgb((int) Math.round(2 * Spot.inflate(piece)), 0, 0));
		for (int i = 0; i < DEBUG.length; i++) out = out.with(DEBUG_X + i, MARKER_Y, DEBUG[i]);
		return out;
	}

	/**
	 * Art on a garment texture at texel column {@code x} (its top-left; the cell's row, centred
	 * vertically), clipped to the part's side rows — a big patch hangs over its neighbours, never
	 * off its part. The part's strip is a loop round the box, so what hangs off either end of it
	 * comes round to the other end (past the outer face of a limb lies its back face).
	 *
	 * <p>A cell on the box's <b>top</b> face (the shoulders) is the other case: that face's four
	 * edges have no neighbour in the layout to continue onto — the strip is a loop round the box's
	 * sides, not over the top of it — so art bigger than the cell is <b>clipped</b> to the face,
	 * both ways, and nothing of it is drawn anywhere else. (Bending it over the four edges, the way
	 * a side cell's overhang bends round the corners, is a later version's job.)
	 */
	private static Tex placed(Spot spot, Tex art, int x) {
		int y = spot.v * D + (spot.pxHeight() - art.height) / 2;   // centred in the cell, whatever size the cell is
		Tex out = Tex.blank(W, H);
		boolean any = false;
		if (spot.top()) {
			int x0 = spot.u * D, x1 = x0 + spot.px(), y0 = Spot.TOP_ROW * D, y1 = Spot.FACE_ROW * D;
			for (int ax = 0; ax < art.width; ax++) {
				int column = x + ax;
				if (column < x0 || column >= x1) continue;
				for (int row = Math.max(y, y0); row < Math.min(y + art.height, y1); row++) {
					int p = art.get(ax, row - y);
					if (p != 0) { out = out.with(column, row, p); any = true; }
				}
			}
			require(any, "patch art lands entirely off the " + spot.id() + " cell's face");
			return out;
		}
		int stripStart = Spot.stripStart(spot) * D, stripWidth = Spot.stripWidth(spot) * D;
		require(art.width <= stripWidth, "patch art is wider than the " + spot.id() + " cell's part");
		for (int ax = 0; ax < art.width; ax++) {
			int column = stripStart + Math.floorMod(x + ax - stripStart, stripWidth);
			for (int row = Math.max(y, Spot.FACE_ROW * D); row < Math.min(y + art.height, (Spot.FACE_ROW + Spot.FACE_ROWS) * D); row++) {
				int p = art.get(ax, row - y);
				if (p != 0) { out = out.with(column, row, p); any = true; }
			}
		}
		require(any, "patch art lands entirely off the " + spot.id() + " cell's part");
		return out;
	}

	/**
	 *      * The same, but as vanilla will draw it from a trim texture: the strip wrapped around the
	 * box the way the shader does for a placement ({@link Spot#anchored}), baked texel by texel
	 * — each column of the part's side rows shows the art column the shader would sample there.
	 *
	 * <p>Only body cells are ever asked for ({@link Trims#fits}), so this is only ever the body's
	 * side rows; a cell on a box's top face would have nothing to bake (the top face is not on the
	 * strip's perimeter, so the shader draws it texel for texel) but cannot be a trim anyway.
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
			for (int row = Spot.FACE_ROW * D; row < (Spot.FACE_ROW + Spot.FACE_ROWS) * D; row++) {
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

	/** Left of the marker: R = kind; sided: G = side, B = the face of its strip (or {@value Spot#TOP_FACE}, the box's top face); preview: G = cells in the half, B = instant designs. Base textures have none (0). */
	public static final int MARKER_KIND_X = W - 2, KIND_SIDED = 1, KIND_PREVIEW = 2;
	/** Two left of the marker: R = 2 × the model inflation of the layer the texture is for (the squeeze needs it). */
	public static final int LAYER_X = W - 3;
	/**
	 * Three to six left of the marker: the palette the {@code OVVAR_DEBUG_TOP_FACE_*} switches paint
	 * a box's top faces from — mirrored, unmirrored, no cell matched, a cell matched. A dev switch in
	 * the shader needs somewhere to get a colour, and a coordinate is all a program of ours can
	 * return; every texture of ours carries them. They are in the layout's unused top-right corner
	 * (skin x 56–64), which no box and no mirror strip touches, and inside the library cell the
	 * preview texture reserves for the marker row.
	 */
	public static final int DEBUG_X = W - 7;
	public static final int[] DEBUG = {0xFFFF0000, 0xFF0000FF, 0xFFFF00FF, 0xFF00FF00};
	/** Always transparent in a patch texture: what the shader draws where there is nothing. */
	public static final int BLANK_X = W - 1, BLANK_Y = H / 2 - 2;
	/**
	 * Preview texture tables, four of them side by side from skin texel 40, column-major 16 tall,
	 * {@value #TABLE_COLUMNS} columns each (so {@value #TABLE_SIZE} entries per table, addressed as
	 * {@code (base + slot / 16, slot % 16)}):
	 *
	 * <ol>
	 *   <li>cell index in the half → (u, v, side),</li>
	 *   <li>the same index → (the cell's own width, its height), since a cell is not one size,</li>
	 *   <li>design slot → (library x, y, art cells across),</li>
	 *   <li>the same slot → (art width, art height)</li>
	 * </ol>
	 *
	 * <p>all in texels of this texture. A <b>design slot</b> is {@code design + fit ·
	 * }{@value #FIT_SLOTS}: a patch may ship art at several sizes and which of them is drawn is the
	 * cell's to decide ({@link Patches.Fit}), so the design tables hold a row per (design, fit) and
	 * the shader reads the one the cell it is drawing asks for. {@value #FIT_SLOTS} slots per fit is
	 * two of the {@value #TABLE_COLUMNS} columns each, which is what makes the tables three columns
	 * of skin texels wide.
	 */
	private static final int CELL_TABLE_X = 40 * D, TABLE_COLUMNS = 3 * D, TABLE_SIZE = 16 * TABLE_COLUMNS;
	/** Beside the cell table: the cell's own size, since {@link Spot#BACK_BIG} is not {@value Spot#PX} square. */
	private static final int CELL_SIZE_TABLE_X = CELL_TABLE_X + TABLE_COLUMNS;
	private static final int PATCH_TABLE_X = CELL_SIZE_TABLE_X + TABLE_COLUMNS;
	private static final int PATCH_SIZE_TABLE_X = PATCH_TABLE_X + TABLE_COLUMNS;
	/** Design slots per {@link Patches.Fit} in the design tables: two of their columns. */
	private static final int FIT_SLOTS = 32;
	/**
	 * Preview library: the head rows (skin texels 0..64 × 0..16) as a grid of cells, minus the
	 * tables' columns (40..52) and the cell holding the marker row's texels (60..64 × 12..16).
	 * A design takes a block of cells its art's size, one per art it can be drawn as.
	 */
	private static final int LIBRARY_COLUMNS = 16, LIBRARY_ROWS = 4;

	private static boolean libraryFree(int cx, int cy) {
		int x0 = cx * Spot.SIZE * D, x1 = x0 + Spot.SIZE * D;   // the library cell's own texel columns
		boolean tables = x1 > CELL_TABLE_X && x0 < PATCH_SIZE_TABLE_X + TABLE_COLUMNS;
		return !tables && !(cx == 15 && cy == 3);
	}

	/** First-fit block of w×h cells in the library; returns its top-left in skin texels. Callers should
	 * allocate biggest blocks first so a big art's fit isn't fragmented away by smaller ones before it. */
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
		return Tex.art(name);
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
		files.png(assets.resolve("textures/entity/equipment/" + piece.layer + "/" + chapter.id + "/" + name + ".png"), tex);
	}

	/** Item definition, flat model and texture for one item. */
	private void item(String name, Tex texture) {
		require(texture.width == 16 && texture.height == 16, name + " icon is not 16×16");
		files.json(assets.resolve("items/" + name + ".json"), J.itemDef(MOD + ":item/" + name));
		files.json(assets.resolve("models/item/" + name + ".json"),
				obj("parent", "minecraft:item/generated", "textures", obj("layer0", MOD + ":item/" + name)));
		files.png(assets.resolve("textures/item/" + name + ".png"), texture);
	}

	/**
	 * An action icon, out of use: half the brightness, and a red slash across the bottom-right
	 * corner so it reads as refused and not merely dark (and reads that way to a colour-blind
	 * player too — it is a shape, not only a colour).
	 */
	private static Tex dimmed(Tex icon) {
		Tex out = icon.brightened(DIMMED);
		for (int i = 0; i < 5; i++) {
			out = out.with(15 - i, 11 + i, SLASH).with(Math.max(0, 14 - i), 11 + i, SLASH);
		}
		return out;
	}

	private static final float DIMMED = 0.5f;
	private static final int SLASH = 0xFFCC3333;

	/** The ghost of a sprite: every opaque pixel mixed {@value #GHOST_WHITE} to white — the preview of a patch not sewn yet. */
	private static Tex ghosted(Tex sprite) {
		Tex out = sprite;
		for (int y = 0; y < sprite.height; y++) {
			for (int x = 0; x < sprite.width; x++) {
				int p = sprite.get(x, y);
				if (p != 0) out = out.with(x, y, Tex.mix(p, 0xFFFFFFFF, GHOST_WHITE));
			}
		}
		return out;
	}

	private static final double GHOST_WHITE = 0.6;

	/**
	 * An item whose model is one flat, unlit quad of the texture — no thickness, no sides — for
	 * the stand displays. The quad faces +z in model space; an item display turns it 180° about
	 * y, so the readable side faces the display's -z.
	 */
	private void sprite(String name, Tex texture) {
		require(texture.width == 16 && texture.height == 16, name + " sprite is not 16×16");
		files.json(assets.resolve("items/" + name + ".json"), J.itemDef(MOD + ":item/" + name));
		files.json(assets.resolve("models/item/" + name + ".json"), obj(
				"textures", obj("0", MOD + ":item/" + name, "particle", MOD + ":item/" + name),
				"elements", arr(obj(
						"from", arr(0, 0, 8), "to", arr(16, 16, 8), "shade", false,
						"faces", obj("south", obj("uv", arr(0, 0, 16, 16), "texture", "#0"))))));
		files.png(assets.resolve("textures/item/" + name + ".png"), texture);
	}

	private static void require(boolean ok, String message) {
		if (!ok) throw new IllegalStateException("[" + MOD + " datagen] " + message);
	}
}
