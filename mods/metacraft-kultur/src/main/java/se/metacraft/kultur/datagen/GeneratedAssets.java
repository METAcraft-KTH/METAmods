package se.metacraft.kultur.datagen;

import com.google.gson.JsonObject;
import se.metacraft.kultur.Kultur;
import se.metacraft.kultur.catalogue.Catalogue;
import se.metacraft.kultur.drinks.DrinkKind;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static se.metacraft.kultur.datagen.J.obj;
import static se.metacraft.kultur.datagen.J.strings;

/**
 * Everything derived from the catalogue and the art: the banner pattern and painting variant
 * definitions, the tags that put free patterns in every loom and paintings on walls, the textures
 * in vanilla's layout ({@link Masks}), the pattern items' models, and the lang file — sixteen
 * colour names per pattern, which is the line count that makes generating it worth it.
 *
 * <p>Fails loudly, naming the entry, when art is missing or the wrong size: a contributor sees the
 * problem at {@code runDatagen}, not as an invisible pattern on a server.
 */
public final class GeneratedAssets implements DataProvider {
	private static final String MOD = Kultur.NAMESPACE;
	/** Vanilla's sixteen dyes, in DyeColor order, with the names its own lang file uses. */
	static final String[][] DYES = {
			{"white", "White"}, {"orange", "Orange"}, {"magenta", "Magenta"}, {"light_blue", "Light Blue"},
			{"yellow", "Yellow"}, {"lime", "Lime"}, {"pink", "Pink"}, {"gray", "Gray"},
			{"light_gray", "Light Gray"}, {"cyan", "Cyan"}, {"purple", "Purple"}, {"blue", "Blue"},
			{"brown", "Brown"}, {"green", "Green"}, {"red", "Red"}, {"black", "Black"}};

	private final Path assets, data, minecraftData;
	private Writes files;

	public GeneratedAssets(FabricPackOutput output) {
		this.assets = output.getOutputFolder(PackOutput.Target.RESOURCE_PACK).resolve(MOD);
		this.data = output.getOutputFolder(PackOutput.Target.DATA_PACK).resolve(MOD);
		this.minecraftData = output.getOutputFolder(PackOutput.Target.DATA_PACK).resolve("minecraft");
	}

	@Override
	public String getName() {
		return "Kultur catalogue-derived assets";
	}

	@Override
	public CompletableFuture<?> run(CachedOutput output) {
		this.files = new Writes(output);
		Catalogue catalogue = Catalogue.load();
		Map<String, String> lang = new LinkedHashMap<>();
		lang.put("itemGroup." + MOD, "Kultur");
		List<String> free = new ArrayList<>(), placeable = new ArrayList<>(), loomItems = new ArrayList<>();

		for (Catalogue.Owned<Catalogue.Pattern> owned : catalogue.patterns()) {
			Catalogue.Pattern p = owned.value();
			String chapter = owned.chapter().id();
			String id = MOD + ":" + p.id();
			files.json(data.resolve("banner_pattern/" + p.id() + ".json"),
					obj("asset_id", id, "translation_key", "block." + MOD + ".banner." + p.id()));
			for (String[] dye : DYES) lang.put("block." + MOD + ".banner." + p.id() + "." + dye[0], dye[1] + " " + p.name());

			BufferedImage banner = art(chapter, "banner", p.id(), true);
			files.png(assets.resolve("textures/entity/banner/" + p.id() + ".png"),
					Masks.png(normalised(chapter, "banner", p.id(), () -> Masks.banner(banner))));
			BufferedImage shield = art(chapter, "shield", p.id(), false);
			files.png(assets.resolve("textures/entity/shield/" + p.id() + ".png"),
					Masks.png(shield != null
							? normalised(chapter, "shield", p.id(), () -> Masks.shield(shield))
							: normalised(chapter, "banner", p.id(), () -> Masks.shieldFromBanner(banner))));

			if (p.item()) {
				files.json(data.resolve("tags/banner_pattern/pattern_item/" + p.id() + ".json"), tag(List.of(id)));
				BufferedImage icon = art(chapter, "item", p.id(), true);
				if (icon.getWidth() != 16 || icon.getHeight() != 16) {
					throw new IllegalStateException("art/kultur/" + chapter + "/item/" + p.id() + ".png is " + icon.getWidth() + "×" + icon.getHeight() + ", wanted 16×16");
				}
				String item = p.itemPath();
				files.png(assets.resolve("textures/item/" + item + ".png"), Masks.png(icon));
				files.json(assets.resolve("models/item/" + item + ".json"),
						obj("parent", "minecraft:item/generated", "textures", obj("layer0", MOD + ":item/" + item)));
				files.json(assets.resolve("items/" + item + ".json"),
						obj("model", obj("type", "minecraft:model", "model", MOD + ":item/" + item)));
				lang.put("item." + MOD + "." + item, p.name() + " Banner Pattern");
				// The loom's pattern slot takes an item only if it is in this tag AND carries the component.
				loomItems.add(MOD + ":" + item);
			} else {
				free.add(id);
			}
		}

		for (Catalogue.Owned<Catalogue.Painting> owned : catalogue.paintings()) {
			Catalogue.Painting q = owned.value();
			String chapter = owned.chapter().id();
			String id = MOD + ":" + q.id();
			files.json(data.resolve("painting_variant/" + q.id() + ".json"), obj(
					"asset_id", id, "width", q.width(), "height", q.height(),
					"title", obj("translate", "painting." + MOD + "." + q.id() + ".title"),
					"author", obj("translate", "painting." + MOD + "." + q.id() + ".author")));
			lang.put("painting." + MOD + "." + q.id() + ".title", q.title());
			lang.put("painting." + MOD + "." + q.id() + ".author", q.author());
			BufferedImage png = art(chapter, "painting", q.id(), true);
			if (png.getWidth() != 16 * q.width() || png.getHeight() != 16 * q.height()) {
				throw new IllegalStateException("art/kultur/" + chapter + "/painting/" + q.id() + ".png is " + png.getWidth() + "×" + png.getHeight()
						+ ", wanted " + (16 * q.width()) + "×" + (16 * q.height()) + " for " + q.width() + "×" + q.height() + " blocks");
			}
			files.png(assets.resolve("textures/painting/" + q.id() + ".png"), Masks.png(png));
			placeable.add(id);
		}

		// The drinks without Brewery (metacraft.kultur.drinks): Inebriated's own name (a vanilla client sees
		// Luck, renamed by drinks_pack/assets/minecraft/lang) and alcohol poisoning's death messages.
		lang.put("effect." + MOD + ".inebriated", "Inebriated");
		// A vanilla client names a potion's bottles and tipped arrows from the potion's name, under vanilla's keys.
		for (DrinkKind kind : DrinkKind.values()) {
			if (kind == DrinkKind.NYCKELN) continue;
			lang.put("item.minecraft.potion.effect." + kind.potionName(), kind.displayName());
			lang.put("item.minecraft.splash_potion.effect." + kind.potionName(), "Splash " + kind.displayName());
			lang.put("item.minecraft.lingering_potion.effect." + kind.potionName(), "Lingering " + kind.displayName());
			lang.put("item.minecraft.tipped_arrow.effect." + kind.potionName(), "Arrow of " + kind.displayName());
		}
		lang.put("item." + MOD + ".nyckeln", DrinkKind.NYCKELN.displayName());
		lang.put("death.attack." + MOD + ".alcohol_poisoning", "%1$s was killed by alcohol poisoning");
		lang.put("death.attack." + MOD + ".alcohol_poisoning.player", "%1$s was killed by alcohol poisoning while fighting %2$s");

		files.json(minecraftData.resolve("tags/banner_pattern/no_item_required.json"), tag(free));
		files.json(minecraftData.resolve("tags/painting_variant/placeable.json"), tag(placeable));
		files.json(minecraftData.resolve("tags/item/loom_patterns.json"), tag(loomItems));
		JsonObject langJson = new JsonObject();
		lang.forEach(langJson::addProperty);
		files.json(assets.resolve("lang/en_us.json"), langJson);
		return files.allOf();
	}

	private static JsonObject tag(List<String> values) {
		return obj("replace", false, "values", strings(values));
	}

	/** A PNG from {@code art/kultur/<chapter>/<kind>/<id>.png}; null when absent and not required. */
	private static BufferedImage art(String chapter, String kind, String id, boolean required) {
		String resource = "/art/kultur/" + chapter + "/" + kind + "/" + id + ".png";
		try (InputStream in = GeneratedAssets.class.getResourceAsStream(resource)) {
			if (in == null) {
				if (required) throw new IllegalStateException("missing art: src/main/resources" + resource);
				return null;
			}
			try {
				return Masks.read(in);
			} catch (UncheckedIOException e) {
				throw new IllegalStateException("art/kultur/" + chapter + "/" + kind + "/" + id + ".png: not a PNG");
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * Runs a {@link Masks} operation on already-loaded art, naming the source PNG's path when it
	 * fails: a contributor sees which file is wrong, not just that {@code Masks} rejected some image.
	 */
	private static BufferedImage normalised(String chapter, String kind, String id, Supplier<BufferedImage> op) {
		try {
			return op.get();
		} catch (IllegalArgumentException | UncheckedIOException e) {
			throw new IllegalStateException("art/kultur/" + chapter + "/" + kind + "/" + id + ".png: " + e.getMessage(), e);
		}
	}
}
