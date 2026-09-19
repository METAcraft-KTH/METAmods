package metacraft.ovvar.store;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;

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

	@Override
	public String describe() {
		return "file " + dir.toAbsolutePath();
	}
}
