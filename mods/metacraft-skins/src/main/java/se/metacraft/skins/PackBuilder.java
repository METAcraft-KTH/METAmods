package se.metacraft.skins;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import se.metacraft.skins.item.SkinBox;
import se.metacraft.skins.item.SkinToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.util.InclusiveRange;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Zips the loaded skins' files into the skins pack, in memory. Entries go in sorted order with
 * one fixed time, so the same skins always give the same bytes and the same SHA-1 (ovvar's
 * ComboPack does the same).
 */
final class PackBuilder {
	private PackBuilder() {}

	/** The earliest time a zip entry can hold; no clock, and no time zone, gets into the bytes. */
	private static final LocalDateTime ENTRY_TIME = LocalDateTime.of(1980, 1, 1, 0, 0);
	/**
	 * {@link SkinFolder#LOCKED_OVERLAY}: one see-through square of grey stained glass, as big as the slot,
	 * facing the viewer at the very front of the model space (z 28 of up to 32), so a Look in its
	 * usual 0–16 box is always behind it; unshaded and lit from the front, as a flat item is.
	 */
	private static final String LOCKED_OVERLAY_MODEL = """
			{
				"gui_light": "front",
				"textures": {
					"glass": { "sprite": "minecraft:block/gray_stained_glass", "force_translucent": true },
					"particle": "minecraft:block/gray_stained_glass"
				},
				"elements": [
					{ "from": [0, 0, 28], "to": [16, 16, 28], "shade": false,
						"faces": { "south": { "uv": [0, 0, 16, 16], "texture": "#glass" } } }
				]
			}
			""";

	/**
	 * The files every build carries: the shared Skin Token's, from the jar's {@code skin-token/}, and
	 * the Skin Box's, from {@code skin-box/} (each model sits outside {@code models/item/} and each
	 * texture right in {@code textures/item/}), with vanilla's vault item definition overridden to draw
	 * a vault marked as a box ({@link SkinBox#LOOK}) with the box's model and any other vault as the
	 * vault ({@link #vault}). At paths no skin's can take (a skin's are always under its own id), and always there.
	 */
	private static final Map<String, byte[]> FIXED_FILES = fixedFiles();

	/**
	 * The pack of these skins and these Skin Boxes: each box with a look of its own adds its model and
	 * textures, and a case to the vault's item definition for a vault marked as that box
	 * ({@link SkinBox#look}). A box without one changes nothing.
	 */
	static SkinsPack build(Collection<SkinFolder.Loaded> skins, Collection<BoxFile.Loaded> boxes) {
		Map<String, byte[]> files = new TreeMap<>(FIXED_FILES);
		files.put("pack.mcmeta", mcmeta().getBytes(StandardCharsets.UTF_8));
		for (SkinFolder.Loaded skin : skins) files.putAll(skin.files());
		Map<String, String> looks = new TreeMap<>();
		for (BoxFile.Loaded box : boxes) {
			box.look().ifPresent(model -> {
				files.putAll(box.files());
				looks.put(SkinBox.look(box.id()), model);
			});
		}
		files.put("assets/minecraft/items/vault.json", vault(looks).getBytes(StandardCharsets.UTF_8));

		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
			for (var file : files.entrySet()) {
				ZipEntry entry = new ZipEntry(file.getKey());
				entry.setTimeLocal(ENTRY_TIME);
				zip.putNextEntry(entry);
				zip.write(file.getValue());
				zip.closeEntry();
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		byte[] data = bytes.toByteArray();
		try {
			return new SkinsPack(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data)), data);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static Map<String, byte[]> fixedFiles() {
		String namespace = SkinToken.MODEL.getNamespace();
		String model = "{ \"model\": { \"type\": \"minecraft:model\", \"model\": \"" + namespace + ":token/skin_token\" } }";
		Map<String, byte[]> files = new TreeMap<>();
		files.put("assets/" + namespace + "/items/" + SkinToken.MODEL.getPath() + ".json", model.getBytes(StandardCharsets.UTF_8));
		// The model's textures already name skins:item/skin_token.
		files.put("assets/" + namespace + "/models/token/skin_token.json", bundled("skin-token/skin_token.json"));
		files.put("assets/" + namespace + "/textures/item/skin_token.png", bundled("skin-token/skin_token.png"));
		// The box's model names skins:item/skin_box.
		files.put("assets/" + SkinBox.BOX_MODEL.getNamespace() + "/models/" + SkinBox.BOX_MODEL.getPath() + ".json", bundled("skin-box/skin_box.json"));
		files.put("assets/" + SkinBox.BOX_MODEL.getNamespace() + "/textures/item/skin_box.png", bundled("skin-box/skin_box.png"));
		// The Locked Look's pane: vanilla's grey stained glass, flat across the whole slot, in front of any Look.
		Identifier overlay = Identifier.parse(SkinFolder.LOCKED_OVERLAY);
		files.put("assets/" + overlay.getNamespace() + "/models/" + overlay.getPath() + ".json", LOCKED_OVERLAY_MODEL.getBytes(StandardCharsets.UTF_8));
		return files;
	}

	/**
	 * Vanilla's vault item definition, overridden: a vault marked {@link SkinBox#LOOK} is drawn with the
	 * shared box model, one marked as a box with its own look ({@code looks}: marker to model id) with
	 * that model, and any other vault as the vault.
	 */
	private static String vault(Map<String, String> looks) {
		StringBuilder cases = new StringBuilder(vaultCase(SkinBox.LOOK, SkinBox.BOX_MODEL.toString()));
		looks.forEach((when, model) -> cases.append(", ").append(vaultCase(when, model)));
		return "{ \"model\": { \"type\": \"minecraft:select\", \"property\": \"minecraft:custom_model_data\", \"index\": 0,"
				+ " \"cases\": [ " + cases + " ],"
				+ " \"fallback\": { \"type\": \"minecraft:model\", \"model\": \"minecraft:block/vault\" } } }";
	}

	private static String vaultCase(String when, String model) {
		return "{ \"when\": \"" + when + "\", \"model\": { \"type\": \"minecraft:model\", \"model\": \"" + model + "\" } }";
	}

	private static byte[] bundled(String path) {
		Path file = FabricLoader.getInstance().getModContainer(MetacraftSkins.MOD_ID).flatMap(mod -> mod.findPath(path))
				.orElseThrow(() -> new IllegalStateException("the jar has no " + path));
		try {
			return Files.readAllBytes(file);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static String mcmeta() {
		var format = SharedConstants.getCurrentVersion().packVersion(PackType.CLIENT_RESOURCES);
		var section = new PackMetadataSection(Component.literal("METAcraft skins"), new InclusiveRange<>(format));
		JsonObject root = new JsonObject();
		root.add(PackMetadataSection.CLIENT_TYPE.name(), PackMetadataSection.CLIENT_TYPE.codec().encodeStart(JsonOps.INSTANCE, section).getOrThrow());
		return new GsonBuilder().setPrettyPrinting().create().toJson(root);
	}
}
