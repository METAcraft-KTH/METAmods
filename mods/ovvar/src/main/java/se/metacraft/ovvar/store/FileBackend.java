package se.metacraft.ovvar.store;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import se.metacraft.ovvar.Ovvar;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Wardrobes as files: {@code <dir>/<owner uuid>.json}. The default store — the world's own
 * {@code ovvar/wardrobes} — or any directory the servers share. The version check re-reads the
 * file before writing and the write is a temp file renamed into place, which is safe on a local
 * disk and good enough on a shared mount for players who can only be on one server at a time; a
 * real network wants {@link JdbcBackend}.
 */
public final class FileBackend implements WardrobeBackend {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private final Path dir;

	public FileBackend(Path dir) {
		this.dir = dir;
	}

	private Path file(UUID owner) {
		return dir.resolve(owner + ".json");
	}

	@Override
	public Optional<Wardrobe> load(UUID owner) throws IOException {
		return Optional.ofNullable(read(file(owner)));
	}

	private static Wardrobe read(Path path) throws IOException {
		if (!Files.exists(path)) return null;
		try {
			JsonElement json = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
			return Wardrobe.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(msg -> new IOException(path + ": " + msg));
		} catch (RuntimeException e) {
			throw new IOException(path + ": " + e.getMessage(), e);
		}
	}

	@Override
	public synchronized boolean store(UUID owner, Wardrobe next, long expectedVersion) throws IOException {
		Path path = file(owner);
		Wardrobe current = read(path);
		long held = current == null ? 0 : current.version();
		if (held != expectedVersion) return false;
		Files.createDirectories(path.getParent());
		Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
		Files.writeString(tmp, GSON.toJson(Wardrobe.CODEC.encodeStart(JsonOps.INSTANCE, next).getOrThrow(IOException::new)), StandardCharsets.UTF_8);
		try {
			Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
		}
		return true;
	}

	public Path dir() {
		return dir;
	}

	/** Every owner with a file here, in no particular order. */
	public List<UUID> owners() throws IOException {
		if (!Files.isDirectory(dir)) return List.of();
		List<UUID> out = new ArrayList<>();
		try (Stream<Path> files = Files.list(dir)) {
			for (Path path : (Iterable<Path>) files::iterator) {
				String name = path.getFileName().toString();
				if (!name.endsWith(".json")) continue;
				try {
					out.add(UUID.fromString(name.substring(0, name.length() - ".json".length())));
				} catch (IllegalArgumentException ignored) {
					// not a wardrobe
				}
			}
		}
		return out;
	}

	@Override
	public List<Wardrobe> all() throws IOException {
		if (!Files.isDirectory(dir)) return List.of();
		List<Wardrobe> out = new ArrayList<>();
		try (Stream<Path> files = Files.list(dir)) {
			for (Path path : (Iterable<Path>) files::iterator) {
				if (!path.getFileName().toString().endsWith(".json")) continue;
				try {
					Wardrobe wardrobe = read(path);
					if (wardrobe != null) out.add(wardrobe);
				} catch (IOException e) {
					Ovvar.LOGGER.warn("[ovvar] wardrobes: skipping unreadable {}: {}", path, e.getMessage());
				}
			}
		}
		return out;
	}

	/** Moves an owner's file aside as {@code <uuid>.json.imported}, so a second import skips it. */
	public void markImported(UUID owner) throws IOException {
		Path path = file(owner);
		if (Files.exists(path)) Files.move(path, path.resolveSibling(path.getFileName() + ".imported"), StandardCopyOption.REPLACE_EXISTING);
	}

	@Override
	public String describe() {
		return "file " + dir.toAbsolutePath();
	}
}
