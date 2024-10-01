package se.datasektionen.mc.metacraft_core.util;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
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
	public static final String FORCED_RESPAWN_ANGLE = "forcedRespawnAngle";

	private boolean disableArmorDamage = false;
	@Nullable
	private RegistryKey<World> forcedRespawnWorld;
	@Nullable
	private Vec3d forcedRespawnPos;
	private float forcedRespawnAngle;

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
		nbt.putFloat(FORCED_RESPAWN_ANGLE, forcedRespawnAngle);
		return nbt;
	}

	private void readNBT(NbtCompound nbt) {
		this.disableArmorDamage = nbt.getBoolean(NO_ARMOR_DAMAGE);
		if (nbt.contains(FORCED_RESPAWN_WORLD)) {
			Identifier worldIdentifier = Identifier.tryParse(nbt.getString(FORCED_RESPAWN_WORLD));
			if (worldIdentifier != null) {
				this.forcedRespawnWorld = RegistryKey.of(RegistryKeys.WORLD, worldIdentifier);
			}
		}
		if (nbt.contains(FORCED_RESPAWN_X) && nbt.contains(FORCED_RESPAWN_Y) && nbt.contains(FORCED_RESPAWN_Z)) {
			this.forcedRespawnPos = new Vec3d(
					nbt.getDouble(FORCED_RESPAWN_X),
					nbt.getDouble(FORCED_RESPAWN_Y),
					nbt.getDouble(FORCED_RESPAWN_Z)
			);
		}
		this.forcedRespawnAngle = nbt.getFloat(FORCED_RESPAWN_ANGLE);
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
	public Vec3d getForcedRespawnPos() {
		return this.forcedRespawnPos;
	}

	public float getForcedRespawnAngle() {
		return forcedRespawnAngle;
	}

	public void setForcedRespawn(RegistryKey<World> world, Vec3d pos, float angle) {
		this.forcedRespawnWorld = world;
		this.forcedRespawnPos = pos;
		this.forcedRespawnAngle = angle;
		this.markDirty();
	}

	public void unsetForcedRespawn() {
		this.forcedRespawnWorld = null;
		this.forcedRespawnPos = null;
		this.forcedRespawnAngle = 0;
		this.markDirty();
	}
}
