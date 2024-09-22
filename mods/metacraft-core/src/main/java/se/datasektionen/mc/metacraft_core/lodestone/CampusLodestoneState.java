package se.datasektionen.mc.metacraft_core.lodestone;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;
import se.datasektionen.mc.metacraft_core.METAcraftCore;

public class CampusLodestoneState extends PersistentState {
	private static final String KEY = METAcraftCore.NAMESPACE + "-campus-lodestone";

	private static PersistentState.Type<CampusLodestoneState> getType(MinecraftServer server) {
		return new Type<>(() -> new CampusLodestoneState(server), (nbt, lookup) -> fromNBT(server, nbt), null);
	}

	private static CampusLodestoneState fromNBT(MinecraftServer server, NbtCompound nbt) {
		var data = new CampusLodestoneState(server);
		data.readNBT(nbt);
		return data;
	}

	private RegistryKey<World> campusWorld;
	private BlockPos campusLodestonePos;

	public CampusLodestoneState(MinecraftServer server) {
		this.campusWorld = server.getOverworld().getRegistryKey();
		this.campusLodestonePos = new BlockPos(BlockPos.ZERO);
	}

	public static CampusLodestoneState getInstance(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(getType(server), KEY);
	}

	public RegistryKey<World> getCampusWorld() {
		return this.campusWorld;
	}

	public BlockPos getCampusLodestonePos() {
		return this.campusLodestonePos;
	}

	public void setCampusLocation(World world, BlockPos lodestonePos) {
		this.campusWorld = world.getRegistryKey();
		this.campusLodestonePos = lodestonePos;
		this.markDirty();
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		nbt.putString("campusWorld", this.campusWorld.getValue().toString());
		nbt.putInt("campusLodestoneX", this.campusLodestonePos.getX());
		nbt.putInt("campusLodestoneY", this.campusLodestonePos.getY());
		nbt.putInt("campusLodestoneZ", this.campusLodestonePos.getZ());
		return nbt;
	}

	public void readNBT(NbtCompound nbt) {
		Identifier campusWorldIdentifier = Identifier.tryParse(nbt.getString("campusWorld"));
		if (campusWorldIdentifier != null) {
			this.campusWorld = RegistryKey.of(RegistryKeys.WORLD, campusWorldIdentifier);
		}
		this.campusLodestonePos = new BlockPos(
			nbt.getInt("campusLodestoneX"),
			nbt.getInt("campusLodestoneY"),
			nbt.getInt("campusLodestoneZ")
		);
	}
}
