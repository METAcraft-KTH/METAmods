package nu.metacraft.nether_rifts.rift;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import nu.metacraft.lib.util.SavedDataTypeCache;
import nu.metacraft.nether_rifts.NetherRifts;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Every rift in one level, and which rift a block or frame position belongs to. */
public class RiftData extends SavedData {

	public static final Codec<RiftData> CODEC = Rift.CODEC.listOf().xmap(RiftData::new, d -> List.copyOf(d.rifts.values()));

	private static final SavedDataTypeCache.Type<RiftData, ServerLevel> TYPE = new SavedDataTypeCache.Type<>(
			level -> new SavedDataType<>(NetherRifts.getID("rifts"), RiftData::new, CODEC, null)
	);

	private final Map<UUID, Rift> rifts = new LinkedHashMap<>();
	/** Block and frame positions to the rifts claiming them: two rifts can share a frame block. */
	private final Map<BlockPos, List<UUID>> index = new HashMap<>();

	public RiftData() {
	}

	private RiftData(List<Rift> loaded) {
		loaded.forEach(this::put);
	}

	public static RiftData get(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(SavedDataTypeCache.get(level, TYPE));
	}

	/** Torn and raiding rifts across every level. */
	public static int activeRaids(MinecraftServer server) {
		int count = 0;
		for (var level : server.getAllLevels()) {
			for (var rift : get(level).all()) {
				if (rift.stage().raiding()) {
					count++;
				}
			}
		}
		return count;
	}

	public Rift open(ServerLevel level, RiftShape shape, boolean crying) {
		var rift = new Rift(shape, crying);
		put(rift);
		rift.placeBlocks(level);
		setDirty();
		return rift;
	}

	private void put(Rift rift) {
		rifts.put(rift.id(), rift);
		rift.shape().blocks().forEach(p -> index.computeIfAbsent(p, k -> new ArrayList<>(1)).add(rift.id()));
		rift.shape().frame().forEach(p -> index.computeIfAbsent(p, k -> new ArrayList<>(1)).add(rift.id()));
	}

	public void remove(Rift rift) {
		rifts.remove(rift.id());
		for (var pos : rift.shape().blocks()) {
			unindex(pos, rift.id());
		}
		for (var pos : rift.shape().frame()) {
			unindex(pos, rift.id());
		}
		setDirty();
	}

	private void unindex(BlockPos pos, UUID id) {
		var ids = index.get(pos);
		if (ids != null && ids.remove(id) && ids.isEmpty()) {
			index.remove(pos);
		}
	}

	/** The rift whose blocks include the position, else a rift whose frame does. */
	public Optional<Rift> at(BlockPos pos) {
		var ids = index.get(pos);
		if (ids == null) {
			return Optional.empty();
		}
		Rift framing = null;
		for (var id : ids) {
			var rift = rifts.get(id);
			if (rift == null) {
				continue;
			}
			if (rift.shape().blocks().contains(pos)) {
				return Optional.of(rift);
			}
			if (framing == null) {
				framing = rift;
			}
		}
		return Optional.ofNullable(framing);
	}

	/** Whether a live rift other than {@code rift} claims the position: a frame block two rifts share. */
	public boolean claimedByOther(BlockPos pos, Rift rift) {
		var ids = index.get(pos);
		if (ids == null) {
			return false;
		}
		for (var id : ids) {
			if (!id.equals(rift.id()) && rifts.containsKey(id)) {
				return true;
			}
		}
		return false;
	}

	public Optional<Rift> byId(UUID id) {
		return Optional.ofNullable(rifts.get(id));
	}

	public List<Rift> all() {
		return List.copyOf(rifts.values());
	}

	public void tick(ServerLevel level) {
		if (rifts.isEmpty()) {
			return;
		}
		for (var rift : all()) {
			rift.tick(level, this);
		}
		setDirty();
	}
}
