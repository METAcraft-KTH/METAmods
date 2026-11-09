package se.metacraft.skins;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.util.ExtraCodecs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Ownership, one readable JSON file per player: {@code <dir>/<uuid>.json}. It is the only place
 * ownership is recorded.
 * <pre>{@code
 * { "uuid": "…", "name": "Alice",
 *   "skins": { "candy_sword": { "claimed": "2026-10-24T10:00:00Z", "application": { "id": "…", "world": "…", "kind": "sword" } },
 *              "crown": { "claimed": "…", "tint": { "color": "#4F7942" } } } }
 * }</pre>
 * <ul>
 *   <li><b>The running server's copy wins.</b> A file is read once, the first time its player is
 *       needed, and from then on the copy in memory is the truth; every change writes it whole.</li>
 *   <li><b>Saved at once.</b> {@link #save} writes a temporary file and moves it into place before
 *       the change counts. If that fails, the copy in memory is left as it was.</li>
 *   <li><b>Damaged files are never overwritten.</b> A file that doesn't parse, or holds a field this
 *       mod doesn't know, is logged and read again on every look until it's fixed (it's never
 *       cached), and there's no record to save over it.</li>
 * </ul>
 * The file goes through codecs but not metacraft-lib's {@code JsonHelper}, which can't keep those
 * promises: its save swallows a failure and overwrites in place, and its load hides why a file failed.
 */
final class OwnershipStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	/**
	 * An application: its own id (an item carrying another one is stale), the world that stamped it,
	 * and the skin's Kind when it was applied (after a Kind change it's on no item). Files written
	 * before the Kind was kept have none, and count as the skin's current Kind.
	 */
	record Application(UUID id, UUID world, Optional<Kind> kind) {
		static final Codec<Application> CODEC = StrictCodec.of(RecordCodecBuilder.mapCodec(instance -> instance.group(
				UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(Application::id),
				UUIDUtil.STRING_CODEC.fieldOf("world").forGetter(Application::world),
				Kind.CODEC.optionalFieldOf("kind").forGetter(Application::kind)
		).apply(instance, Application::new)));
	}

	/** An owned skin; Unapplied without an {@code application}. A Dyeable Hat's {@code tint} is the colour it comes back in when applied. */
	record Owned(Instant claimed, Optional<Application> application, Optional<Tint> tint) {
		static final Codec<Owned> CODEC = StrictCodec.of(RecordCodecBuilder.mapCodec(instance -> instance.group(
				ExtraCodecs.INSTANT_ISO8601.fieldOf("claimed").forGetter(Owned::claimed),
				Application.CODEC.optionalFieldOf("application").forGetter(Owned::application),
				Tint.CODEC.optionalFieldOf("tint").forGetter(Owned::tint)
		).apply(instance, Owned::new)));

		Owned withApplication(Optional<Application> next) {
			return new Owned(claimed, next, tint);
		}

		Owned withTint(Optional<Tint> next) {
			return new Owned(claimed, application, next);
		}
	}

	/** A player's record; {@code skins} is sorted by id. */
	record PlayerRecord(UUID uuid, Optional<String> name, SortedMap<String, Owned> skins) {
		static final Codec<PlayerRecord> CODEC = StrictCodec.of(RecordCodecBuilder.mapCodec(instance -> instance.group(
				UUIDUtil.STRING_CODEC.fieldOf("uuid").forGetter(PlayerRecord::uuid),
				Codec.STRING.optionalFieldOf("name").forGetter(PlayerRecord::name),
				Codec.unboundedMap(Codec.STRING, Owned.CODEC).<SortedMap<String, Owned>>xmap(m -> Collections.unmodifiableSortedMap(new TreeMap<>(m)), m -> m)
						.fieldOf("skins").forGetter(PlayerRecord::skins)
		).apply(instance, PlayerRecord::new)));

		static PlayerRecord empty(UUID uuid) {
			return new PlayerRecord(uuid, Optional.empty(), Collections.emptySortedMap());
		}

		PlayerRecord with(String skinId, Owned owned) {
			SortedMap<String, Owned> next = new TreeMap<>(skins);
			next.put(skinId, owned);
			return new PlayerRecord(uuid, name, Collections.unmodifiableSortedMap(next));
		}

		PlayerRecord without(String skinId) {
			SortedMap<String, Owned> next = new TreeMap<>(skins);
			next.remove(skinId);
			return new PlayerRecord(uuid, name, Collections.unmodifiableSortedMap(next));
		}

		PlayerRecord named(String lastKnownName) {
			return new PlayerRecord(uuid, Optional.of(lastKnownName), skins);
		}
	}

	private final Path dir;
	private final Map<UUID, PlayerRecord> cache = new HashMap<>();
	/** The damage last logged per player, so a file looked at again and again is logged once per fault. */
	private final Map<UUID, String> loggedDamage = new HashMap<>();

	OwnershipStore(Path dir) {
		this.dir = dir;
	}

	/** Every player with a file, damaged or not. */
	synchronized List<UUID> players() {
		if (!Files.isDirectory(dir)) return List.of();
		List<UUID> players = new ArrayList<>();
		try (Stream<Path> files = Files.list(dir)) {
			for (Path file : files.toList()) {
				String name = file.getFileName().toString();
				if (!name.endsWith(".json")) continue;
				try {
					players.add(UUID.fromString(name.substring(0, name.length() - ".json".length())));
				} catch (IllegalArgumentException e) {
					// not a player's file
				}
			}
		} catch (IOException e) {
			MetacraftSkins.LOGGER.error("[{}] cannot list {}", MetacraftSkins.MOD_ID, dir, e);
		}
		return players;
	}

	Path file(UUID player) {
		return dir.resolve(player + ".json");
	}

	/** The player's record (an empty one if they have no file), or why their file is damaged, in words for an admin. */
	synchronized DataResult<PlayerRecord> read(UUID player) {
		PlayerRecord cached = cache.get(player);
		if (cached != null) return DataResult.success(cached);
		Path path = file(player);
		return (Files.exists(path) ? parse(player, path) : DataResult.success(PlayerRecord.empty(player)))
				.ifSuccess(record -> {
					cache.put(player, record);
					if (loggedDamage.remove(player) != null) MetacraftSkins.LOGGER.info("[{}] {} reads again", MetacraftSkins.MOD_ID, path);
				})
				.ifError(damage -> {
					if (!damage.message().equals(loggedDamage.put(player, damage.message()))) {
						MetacraftSkins.LOGGER.error("[{}] {} is damaged and will not be overwritten; until it's fixed this player has no skins and their "
								+ "skin actions are refused: {}", MetacraftSkins.MOD_ID, path, damage.message());
					}
				});
	}

	/** Writes the record whole and only then makes it the copy in memory. */
	synchronized void save(PlayerRecord record) throws IOException {
		String json = GSON.toJson(PlayerRecord.CODEC.encodeStart(JsonOps.INSTANCE, record).getOrThrow(IOException::new));
		Path path = file(record.uuid());
		Files.createDirectories(dir);
		Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
		Files.writeString(tmp, json + "\n", StandardCharsets.UTF_8);
		try {
			Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
		}
		cache.put(record.uuid(), record);
	}

	/** The whole record or an error, never a partial one: saved, it would lose what it left out. */
	private static DataResult<PlayerRecord> parse(UUID player, Path path) {
		JsonElement json;
		try {
			json = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
		} catch (IOException | JsonParseException e) {
			return DataResult.error(() -> e.getMessage() != null ? e.getMessage() : e.toString());
		}
		return PlayerRecord.CODEC.parse(JsonOps.INSTANCE, json)
				.mapOrElse(DataResult::success, error -> DataResult.<PlayerRecord>error(error.messageSupplier()))
				.flatMap(record -> record.uuid().equals(player) ? DataResult.success(record)
						: DataResult.error(() -> "its uuid " + record.uuid() + " is not the one in its file name"));
	}
}
