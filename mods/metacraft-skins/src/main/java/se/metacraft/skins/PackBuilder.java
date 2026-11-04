package se.metacraft.skins;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import se.metacraft.skins.item.SkinBox;
import se.metacraft.skins.item.SkinToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
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
	 * The files every build carries: the shared Skin Token's, from the jar's {@code skin-token/} (its
	 * model sits outside {@code models/item/} and its texture right in {@code textures/item/}), and the
	 * ominous vault's item definition for ominous Skin Boxes, which reuses vanilla's block model. All
	 * at paths no skin's can take (a skin's are always under its own id), and always there, so a box's
	 * {@code ominous} never changes the pack.
	 */
	private static final Map<String, byte[]> TOKEN_FILES = tokenFiles();

	static SkinsPack build(Collection<SkinFolder.Loaded> skins) {
		Map<String, byte[]> files = new TreeMap<>(TOKEN_FILES);
		files.put("pack.mcmeta", mcmeta().getBytes(StandardCharsets.UTF_8));
		for (SkinFolder.Loaded skin : skins) files.putAll(skin.files());

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

	private static Map<String, byte[]> tokenFiles() {
		String namespace = SkinToken.MODEL.getNamespace();
		String model = "{ \"model\": { \"type\": \"minecraft:model\", \"model\": \"" + namespace + ":token/skin_token\" } }";
		Map<String, byte[]> files = new TreeMap<>();
		files.put("assets/" + namespace + "/items/" + SkinToken.MODEL.getPath() + ".json", model.getBytes(StandardCharsets.UTF_8));
		// The model's textures already name skins:item/skin_token.
		files.put("assets/" + namespace + "/models/token/skin_token.json", bundled("skin-token/skin_token.json"));
		files.put("assets/" + namespace + "/textures/item/skin_token.png", bundled("skin-token/skin_token.png"));
		String ominous = "{ \"model\": { \"type\": \"minecraft:model\", \"model\": \"minecraft:block/vault_ominous\" } }";
		files.put("assets/" + SkinBox.OMINOUS_MODEL.getNamespace() + "/items/" + SkinBox.OMINOUS_MODEL.getPath() + ".json", ominous.getBytes(StandardCharsets.UTF_8));
		return files;
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
