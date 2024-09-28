package se.datasektionen.mc.metacraft_core.util;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import se.datasektionen.mc.metacraft_core.METAcraftCore;

public class METAcraftCoreData extends PersistentState {
	private static final String KEY = METAcraftCore.NAMESPACE + "-core-data";
	public static final String NO_ARMOR_DAMAGE = "disableArmorDamage";
	public static final String FORCED_RESPAWN_WORLD = "forcedRespawnWorld";
	public static final String FORCED_RESPAWN_X = "forcedRespawnX";
	public static final String FORCED_RESPAWN_Y = "forcedRespawnY";
	public static final String FORCED_RESPAWN_Z = "forcedRespawnZ";

	private boolean disableArmorDamage = false;
	@Nullable
	private RegistryKey<World> forcedRespawnWorld;
	@Nullable
	private BlockPos forcedRespawnPos;

	public METAcraftCoreData(MinecraftServer server) {
	}

	private static PersistentState.Type<METAcraftCoreData> getType(MinecraftServer server) {
		return new Type<>(() -> new METAcraftCoreData(server), (nbt, lookup) -> fromNBT(server, nbt), null);
	}

	private static METAcraftCoreData fromNBT(MinecraftServer server, NbtCompound nbt) {
		var data = new METAcraftCoreData(server);
		data.readNBT(nbt);
		return data;
	}

	public static METAcraftCoreData getInstance(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(getType(server), KEY);
	}
	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		nbt.putBoolean(NO_ARMOR_DAMAGE, this.disableArmorDamage);
		if (this.forcedRespawnWorld != null) {
			nbt.putString(FORCED_RESPAWN_WORLD, this.forcedRespawnWorld.getValue().toString());
		}
		if (this.forcedRespawnPos != null) {
			nbt.putDouble(FORCED_RESPAWN_X, this.forcedRespawnPos.getX());
			nbt.putDouble(FORCED_RESPAWN_Y, this.forcedRespawnPos.getY());
			nbt.putDouble(FORCED_RESPAWN_Z, this.forcedRespawnPos.getZ());
		}
		return nbt;
	}

	private void readNBT(NbtCompound nbt) {
		this.disableArmorDamage = nbt.getBoolean(NO_ARMOR_DAMAGE);
		Identifier worldIdentifier = Identifier.tryParse(nbt.getString(FORCED_RESPAWN_WORLD));
		if (worldIdentifier != null) {
			this.forcedRespawnWorld = RegistryKey.of(RegistryKeys.WORLD, worldIdentifier);
		}
		this.forcedRespawnPos = new BlockPos(
			nbt.getInt(FORCED_RESPAWN_X),
			nbt.getInt(FORCED_RESPAWN_Y),
			nbt.getInt(FORCED_RESPAWN_Z)
		);
	}

	public boolean isDisableArmorDamage() {
		return disableArmorDamage;
	}

	public void setDisableArmorDamage(boolean disableArmorDamage) {
		this.disableArmorDamage = disableArmorDamage;
		this.markDirty();
	}

	@Nullable
	public RegistryKey<World> getForcedRespawnWorld() {
		return this.forcedRespawnWorld;
	}

	@Nullable
	public BlockPos getForcedRespawnPos() {
		return this.forcedRespawnPos;
	}

	public void setForcedRespawn(RegistryKey<World> world, BlockPos pos) {
		this.forcedRespawnWorld = world;
		this.forcedRespawnPos = pos;
		this.markDirty();
	}

	public void unsetForcedRespawn() {
		this.forcedRespawnWorld = null;
		this.forcedRespawnPos = null;
		this.markDirty();
	}
}
