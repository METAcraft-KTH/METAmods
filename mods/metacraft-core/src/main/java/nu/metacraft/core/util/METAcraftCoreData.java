package nu.metacraft.core.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.core.countdown.Countdown;
import org.jetbrains.annotations.Nullable;
import nu.metacraft.core.METAcraftCore;

import java.util.Optional;

public class METAcraftCoreData extends SavedData {

	public static final Codec<METAcraftCoreData> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
					SpawnPos.CODEC.optionalFieldOf("forced_respawn").forGetter(t -> t.forcedRespawn),
					Countdown.CODEC.optionalFieldOf("countdown").forGetter(t -> t.countdown)
			).apply(instance, METAcraftCoreData::new)
	);

	private Optional<SpawnPos> forcedRespawn;
	private Optional<Countdown> countdown;

	public METAcraftCoreData(
			Optional<SpawnPos> forcedRespawn,
			Optional<Countdown> countdown
	) {
		this.forcedRespawn = forcedRespawn;
		this.countdown = countdown;
		this.countdown.ifPresent(c -> c.setParent(this));
	}

	public METAcraftCoreData() {
		this(Optional.empty(), Optional.empty());
	}

	private static final SavedDataType<METAcraftCoreData> TYPE = new SavedDataType<>(
			METAcraftCore.getID("core_data"), METAcraftCoreData::new, CODEC, null
	);

	public static METAcraftCoreData getInstance(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	@Nullable
	public ResourceKey<Level> getForcedRespawnWorld() {
		return this.forcedRespawn.map(SpawnPos::world).orElse(null);
	}

	@Nullable
	public Vec3 getForcedRespawnPos() {
		return this.forcedRespawn.map(SpawnPos::pos).orElse(null);
	}

	public float getForcedRespawnAngle() {
		return this.forcedRespawn.map(SpawnPos::angle).orElse(0.0f);
	}

	public void setForcedRespawn(ResourceKey<Level> world, Vec3 pos, float angle) {
		this.forcedRespawn = Optional.of(new SpawnPos(pos, world, angle));
		this.setDirty();
	}

	public void unsetForcedRespawn() {
		this.forcedRespawn = Optional.empty();
		this.setDirty();
	}

	public Optional<Countdown> getCountdown() {
		return countdown;
	}

	public void setCountdown(Optional<Countdown> countdown) {
		this.countdown = countdown;
		this.countdown.ifPresent(c -> c.setParent(this));
		this.setDirty();
	}

	public record SpawnPos(Vec3 pos, ResourceKey<Level> world, float angle) {
		public static final Codec<SpawnPos> CODEC = RecordCodecBuilder.create(
				instance -> instance.group(
						Vec3.CODEC.fieldOf("pos").forGetter(SpawnPos::pos),
						Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(SpawnPos::world),
						Codec.FLOAT.fieldOf("angle").forGetter(SpawnPos::angle)
				).apply(instance, SpawnPos::new)
		);
	}
}
