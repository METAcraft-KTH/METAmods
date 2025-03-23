package se.datasektionen.mc.metacraft_season_4.lodestone;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;
import net.minecraft.world.World;
import se.datasektionen.mc.metacraft_core.METAcraftCore;

public class CampusLodestoneState extends PersistentState {

	private static final MapCodec<BlockPos> CAMPUS_LODESTONE_POS = RecordCodecBuilder.mapCodec(
			i -> i.group(
					Codec.INT.fieldOf("campusLodestoneX").forGetter(BlockPos::getX),
					Codec.INT.fieldOf("campusLodestoneY").forGetter(BlockPos::getY),
					Codec.INT.fieldOf("campusLodestoneZ").forGetter(BlockPos::getZ)
			).apply(i, BlockPos::new)
	);

	private static final Codec<CampusLodestoneState> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
					World.CODEC.fieldOf("campusWorld").forGetter(t -> t.campusWorld),
					CAMPUS_LODESTONE_POS.forGetter(t -> t.campusLodestonePos)
			).apply(instance, CampusLodestoneState::new)
	);

	private static final PersistentStateType<CampusLodestoneState> TYPE = new PersistentStateType<>(
			METAcraftCore.NAMESPACE + "-campus-lodestone", CampusLodestoneState::new,
			CampusLodestoneState.CODEC, null
	);

	private RegistryKey<World> campusWorld;
	private BlockPos campusLodestonePos;

	public CampusLodestoneState() {
		this.campusWorld = World.OVERWORLD;
		this.campusLodestonePos = new BlockPos(BlockPos.ZERO);
	}

	public CampusLodestoneState(RegistryKey<World> campusWorld, BlockPos campusLodestonePos) {
		this.campusWorld = campusWorld;
		this.campusLodestonePos = campusLodestonePos;
	}

	public static CampusLodestoneState getInstance(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE);
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
}
