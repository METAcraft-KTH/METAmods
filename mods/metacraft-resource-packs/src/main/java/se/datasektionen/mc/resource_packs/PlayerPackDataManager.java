package se.datasektionen.mc.resource_packs;

import com.mojang.authlib.GameProfile;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import org.pcollections.HashTreePMap;
import org.pcollections.PMap;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.UnaryOperator;

public class PlayerPackDataManager {

	private final AtomicReference<PMap<UUID, PlayerPackEntry>> packDataMap = new AtomicReference<>(HashTreePMap.empty());
	private final Path directory;
	private final Map<UUID, Lock> locks = new ConcurrentHashMap<>();

	public PlayerPackDataManager(MinecraftServer server) {
		this.directory = server.getSavePath(WorldSavePath.ROOT).resolve("metacraft-resource-packs");
		directory.toFile().mkdirs();
	}

	public PlayerPackData getFromPlayer(GameProfile profile) {
		var uuid = profile.getId();
		return packDataMap.get().getOrDefault(uuid, PlayerPackEntry.EMPTY).data;
	}

	public void update(GameProfile profile, UnaryOperator<PlayerPackData> update) {
		var uuid = profile.getId();
		if (!packDataMap.get().containsKey(uuid)) return;
		packDataMap.updateAndGet(
				map -> {
					var e = map.get(uuid);
					if (e == null) return map;
					var oldData = e.data;
					var newData = update.apply(oldData);
					if (newData == oldData) {
						return map;
					} else if (newData != null) {
						return map.plus(uuid, PlayerPackEntry.save(newData));
					} else {
						return map.plus(uuid, PlayerPackEntry.EMPTY_SAVE);
					}
				}
		);
	}

	private Path getPathFor(UUID uuid) {
		return directory.resolve(uuid.toString() + ".dat");
	}

	private Lock getLock(UUID uuid) {
		return locks.computeIfAbsent(uuid, k -> new ReentrantLock());
	}

	private PMap<UUID, PlayerPackEntry> unmarkSave(PMap<UUID, PlayerPackEntry> map) {
		for (var k : map.keySet()) {
			var v = map.get(k);
			if (v.shouldSave()) {
				map = map.plus(k, v.unmarkSaved());
			}
		}
		return map;
	}

	private void saveEntry(UUID id, PlayerPackEntry data) {
		if (data.shouldSave) {
			var lock = getLock(id);
			lock.lock();
			try {
				var path = getPathFor(id);
				var nbt = PlayerPackData.CODEC.encodeStart(NbtOps.INSTANCE, data.data).resultOrPartial(
						ResourcePacks.LOGGER::error
				);
				if (nbt.isPresent()) {
					try {
						NbtIo.write((NbtCompound) nbt.get(), path);
					} catch (IOException e) {
						ResourcePacks.LOGGER.error(e.getMessage(), e);
					}
				}
			} finally {
				lock.unlock();
			}
		}
	}

	public void unloadPlayer(GameProfile profile) {
		var uuid = profile.getId();
		if (!packDataMap.get().containsKey(uuid)) return;
		var data = packDataMap.getAndUpdate(map -> map.minus(uuid)).get(uuid);
		saveEntry(uuid, data);
		locks.remove(uuid);
	}

	public void save() {
		var data = packDataMap.getAndUpdate(this::unmarkSave);
		for (var k : data.keySet()) {
			var v = data.get(k);
			saveEntry(k, v);
		}
	}

	public void loadPlayer(GameProfile profile) {
		var uuid = profile.getId();
		if (packDataMap.get().containsKey(uuid)) return;
		var lock = getLock(uuid);
		lock.lock();
		try {
			var data = NbtIo.read(getPathFor(uuid));
			PlayerPackEntry entry = Optional.ofNullable(data).flatMap(
					d -> PlayerPackData.CODEC.parse(NbtOps.INSTANCE, d).resultOrPartial(
							ResourcePacks.LOGGER::error
					).map(
							loaded -> {
								var checked = loaded.updatePacks();
								if (checked != loaded) {
									return PlayerPackEntry.save(checked);
								} else {
									return PlayerPackEntry.get(checked);
								}
							}
					)
			).orElse(PlayerPackEntry.EMPTY);

			packDataMap.updateAndGet(
					map -> map.plus(uuid, entry)
			);
		} catch (IOException e) {
			ResourcePacks.LOGGER.error(e.getMessage(), e);
		} finally {
			lock.unlock();
		}
	}

	public static PlayerPackDataManager getInstance(MinecraftServer server) {
		return ((PlayerManagerExtension) server.getPlayerManager()).metacraft_resource_packs$getPlayerPackDataManager();
	}

	public record PlayerPackEntry(PlayerPackData data, boolean shouldSave) {
		public static final PlayerPackEntry EMPTY = get(PlayerPackData.EMPTY);

		public static final PlayerPackEntry EMPTY_SAVE = save(PlayerPackData.EMPTY);

		public static PlayerPackEntry get(PlayerPackData data) {
			return new PlayerPackEntry(data, false);
		}

		public static PlayerPackEntry save(PlayerPackData data) {
			return new PlayerPackEntry(data, true);
		}

		public PlayerPackEntry unmarkSaved() {
			if (!shouldSave) return this;
			if (this == EMPTY_SAVE) return EMPTY;
			return get(data);
		}
	}
}
