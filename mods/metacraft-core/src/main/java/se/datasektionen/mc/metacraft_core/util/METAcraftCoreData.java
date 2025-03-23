package se.datasektionen.mc.metacraft_core.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import se.datasektionen.mc.metacraft_core.METAcraftCore;

import java.util.Optional;

public class METAcraftCoreData extends PersistentState {

	public static final Codec<METAcraftCoreData> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
					SpawnPos.CODEC.optionalFieldOf("forced_respawn").forGetter(t -> t.forcedRespawn)
			).apply(instance, METAcraftCoreData::new)
	);

	private Optional<SpawnPos> forcedRespawn;

	public METAcraftCoreData(
			Optional<SpawnPos> forcedRespawn
	) {
		this.forcedRespawn = forcedRespawn;
	}

	public METAcraftCoreData() {
		this(Optional.empty());
	}

	private static final PersistentStateType<METAcraftCoreData> TYPE = new PersistentStateType<>(
			METAcraftCore.NAMESPACE + "-core-data", METAcraftCoreData::new, CODEC, null
	);

	public static METAcraftCoreData getInstance(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE);
	}

	@Nullable
	public RegistryKey<World> getForcedRespawnWorld() {
		return this.forcedRespawn.map(SpawnPos::world).orElse(null);
	}

	@Nullable
	public Vec3d getForcedRespawnPos() {
		return this.forcedRespawn.map(SpawnPos::pos).orElse(null);
	}

	public float getForcedRespawnAngle() {
		return this.forcedRespawn.map(SpawnPos::angle).orElse(0.0f);
	}

	public void setForcedRespawn(RegistryKey<World> world, Vec3d pos, float angle) {
		this.forcedRespawn = Optional.of(new SpawnPos(pos, world, angle));
		this.markDirty();
	}

	public void unsetForcedRespawn() {
		this.forcedRespawn = Optional.empty();
		this.markDirty();
	}

	public record SpawnPos(Vec3d pos, RegistryKey<World> world, float angle) {
		public static final Codec<SpawnPos> CODEC = RecordCodecBuilder.create(
				instance -> instance.group(
						Vec3d.CODEC.fieldOf("pos").forGetter(SpawnPos::pos),
						World.CODEC.fieldOf("dimension").forGetter(SpawnPos::world),
						Codec.FLOAT.fieldOf("angle").forGetter(SpawnPos::angle)
				).apply(instance, SpawnPos::new)
		);
	}
}
