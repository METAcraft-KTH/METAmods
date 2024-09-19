package se.datasektionen.mc.metacraft_core.lodestone;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import se.datasektionen.mc.metacraft_core.METAcraftCore;
import se.datasektionen.mc.metacraft_lib.METAcraftLib;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CampusLodestoneState extends PersistentState {
	private static final String KEY = METAcraftCore.NAMESPACE + "-campus-lodestone";

	private static final MapCodec<Map<UUID, Location>> BACK_LOCATIONS = Codec.unboundedMap(
		Uuids.STRING_CODEC, Location.CODEC
	).fieldOf("back_locations");

	private static PersistentState.Type<CampusLodestoneState> getType(MinecraftServer server) {
		return new Type<>(() -> new CampusLodestoneState(server), (nbt, lookup) -> fromNBT(server, nbt, lookup), null);
	}

	private static CampusLodestoneState fromNBT(MinecraftServer server, NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		var data = new CampusLodestoneState(server);
		data.readNBT(nbt, registryLookup);
		return data;
	}

	public record Location(RegistryKey<World> world, BlockPos lodestonePos) {

		private static final Codec<Location> CODEC = RecordCodecBuilder.create(i -> i.group(
			World.CODEC.fieldOf("world").forGetter(Location::world),
			BlockPos.CODEC.fieldOf("lodestonePos").forGetter(Location::lodestonePos)
		).apply(i, Location::new));
	}

	private Location campusLocation;
	private final Map<UUID, Location> backLocations = new HashMap<>();

	public CampusLodestoneState(MinecraftServer server) {
		this.campusLocation = new Location(server.getOverworld().getRegistryKey(), new BlockPos(BlockPos.ZERO));
	}

	public static CampusLodestoneState getInstance(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(getType(server), KEY);
	}

	public Location getCampusLocation() {
		return this.campusLocation;
	}

	public void setCampusLocation(World world, BlockPos lodestonePos) {
		this.campusLocation = new Location(world.getRegistryKey(), lodestonePos);
	}

	@Nullable
	public Location getBackLocation(PlayerEntity player) {
		return this.backLocations.get(player.getUuid());
	}

	public void setBackLocation(PlayerEntity player, @Nullable Location location) {
		this.backLocations.put(player.getUuid(), location);
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		var ops = registryLookup.getOps(NbtOps.INSTANCE);
		var mapBuilder = ops.mapBuilder();

		BACK_LOCATIONS.encode(this.backLocations, ops, mapBuilder);

		return (NbtCompound) mapBuilder.build(nbt).resultOrPartial(METAcraftLib.LOGGER::error).orElse(nbt);
	}

	public void readNBT(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		var ops = registryLookup.getOps(NbtOps.INSTANCE);
		ops.getMap(nbt).flatMap(
			m -> BACK_LOCATIONS.decode(ops, m)
		).resultOrPartial(METAcraftLib.LOGGER::error).ifPresent(map -> {
			this.backLocations.clear();
			this.backLocations.putAll(map);
		});
	}
}
