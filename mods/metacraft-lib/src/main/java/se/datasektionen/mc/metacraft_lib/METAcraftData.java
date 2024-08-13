package se.datasektionen.mc.metacraft_lib;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Uuids;
import net.minecraft.world.PersistentState;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class METAcraftData extends PersistentState {

	private static final String KEY = METAcraftLib.NAMESPACE + "-data";

	private static final MapCodec<Map<UUID, String>> OFFLINE_NAME_CACHE = Codec.unboundedMap(
			Uuids.STRING_CODEC, Codec.STRING
	).fieldOf("offline_name_cache");

	private static PersistentState.Type<METAcraftData> getType(MinecraftServer server) {
		return new Type<>(() -> new METAcraftData(server), (nbt, lookup) -> fromNBT(server, nbt, lookup), null);
	}

	private static METAcraftData fromNBT(MinecraftServer server, NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		var data = new METAcraftData(server);
		data.readNBT(nbt, registryLookup);
		return data;
	}

	private final MinecraftServer server;

	private final Map<UUID, String> nameCache = new HashMap<>();

	public METAcraftData(MinecraftServer server) {
		this.server = server;
	}

	public static METAcraftData getInstance(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(getType(server), KEY);
	}

	public void setName(UUID id, @Nullable String name) {
		if (name != null) {
			nameCache.put(id, name);
		} else {
			nameCache.remove(id);
		}
		markDirty();
	}

	public String getName(GameProfile profile) {
		if (nameCache.containsKey(profile.getId())) {
			return nameCache.get(profile.getId());
		}
		return profile.getName();
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		var ops = registryLookup.getOps(NbtOps.INSTANCE);
		var mapBuilder = ops.mapBuilder();

		OFFLINE_NAME_CACHE.encode(nameCache, ops, mapBuilder);

		return (NbtCompound) mapBuilder.build(nbt).resultOrPartial(METAcraftLib.LOGGER::error).orElse(nbt);
	}

	public void readNBT(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		var ops = registryLookup.getOps(NbtOps.INSTANCE);
		ops.getMap(nbt).flatMap(
				m -> OFFLINE_NAME_CACHE.decode(ops, m)
		).resultOrPartial(METAcraftLib.LOGGER::error).ifPresent(offlineNameCache -> {
			this.nameCache.clear();
			this.nameCache.putAll(offlineNameCache);
		});
	}
}
