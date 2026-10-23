package se.metacraft.ovvar.pack;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import se.metacraft.ovvar.Ovvar;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.util.InclusiveRange;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Ovvar's own small resource pack, used when metacraft-resource-packs is installed to serve it:
 * only the equipment definitions of the sewn combinations ({@link Combos}). Sewing then never
 * changes the main Polymer pack, so its hash, and the copy every client has cached, stays the
 * same until the mods do. Each build lives in memory under its SHA-1, and the last {@value #KEEP}
 * stay downloadable, so a player pushed one still gets it when a newer build lands before their
 * download starts.
 */
public final class ComboPack {
	private ComboPack() {}

	/** Is the pack served by metacraft-resource-packs? Otherwise the combinations go into the Polymer pack. */
	public static final boolean ACTIVE = FabricLoader.getInstance().isModLoaded("metacraft-resource-packs");
	/** The pack's id on the client: the same for every build, so a push replaces the one before. */
	public static final UUID ID = UUID.nameUUIDFromBytes((Ovvar.MOD_ID + ":combos").getBytes(StandardCharsets.UTF_8));
	private static final int KEEP = 3;
	private static final String PREFIX = Ovvar.MOD_ID + "/";

	public record Version(String sha1, byte[] zip) {
		/** Where the pack server has it (no leading '/'). */
		public String path() {
			return PREFIX + sha1 + ".zip";
		}
	}

	/** Newest first; guarded by itself (served from the pack server's threads). */
	private static final Deque<Version> RECENT = new ArrayDeque<>();

	/** The pack holding these combinations. The same set always gives the same bytes, so the same hash. */
	public static Version build(Set<Combos.KeyedCombo> combos) {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
			put(zip, "pack.mcmeta", mcmeta());
			for (Combos.KeyedCombo key : new TreeSet<>(combos)) {
				Combos.files(key, (path, json) -> put(zip, path, json));
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		byte[] data = bytes.toByteArray();
		try {
			return new Version(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data)), data);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	static void publish(Version version) {
		synchronized (RECENT) {
			RECENT.removeIf(v -> v.sha1().equals(version.sha1()));
			RECENT.addFirst(version);
			while (RECENT.size() > KEEP) RECENT.removeLast();
		}
	}

	static void clear() {
		synchronized (RECENT) {
			RECENT.clear();
		}
	}

	/** The newest build, or null before the first. */
	public static @Nullable Version current() {
		synchronized (RECENT) {
			return RECENT.peekFirst();
		}
	}

	/** The bytes of a kept build by its path, for the pack server; any thread. */
	public static Optional<byte[]> serve(String path) {
		if (!path.startsWith(PREFIX)) return Optional.empty();
		synchronized (RECENT) {
			for (Version v : RECENT) if (v.path().equals(path)) return Optional.of(v.zip());
		}
		return Optional.empty();
	}

	private static String mcmeta() {
		var format = SharedConstants.getCurrentVersion().packVersion(PackType.CLIENT_RESOURCES);
		var section = new PackMetadataSection(Component.literal("Ovvar patch combinations"), new InclusiveRange<>(format));
		JsonObject root = new JsonObject();
		root.add(PackMetadataSection.CLIENT_TYPE.name(), PackMetadataSection.CLIENT_TYPE.codec().encodeStart(JsonOps.INSTANCE, section).getOrThrow());
		return new GsonBuilder().setPrettyPrinting().create().toJson(root);
	}

	private static void put(ZipOutputStream zip, String path, String text) {
		try {
			ZipEntry entry = new ZipEntry(path);
			entry.setTime(0);   // no clock in the bytes: the same combinations hash the same
			zip.putNextEntry(entry);
			zip.write(text.getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
