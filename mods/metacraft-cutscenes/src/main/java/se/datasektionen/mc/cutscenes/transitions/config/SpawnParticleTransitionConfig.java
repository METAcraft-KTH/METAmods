package se.datasektionen.mc.cutscenes.transitions.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.dynamic.Codecs;
import net.minecraft.util.math.Vec3d;
import se.datasektionen.mc.cutscenes.position_ref.PositionRef;
import se.datasektionen.mc.cutscenes.registry.PositionRefRegistry;
import se.datasektionen.mc.cutscenes.registry.TransitionConfigRegistry;
import se.datasektionen.mc.cutscenes.transitions.SpawnParticleTransition;
import se.datasektionen.mc.cutscenes.transitions.Transition;

public record SpawnParticleTransitionConfig(
		ParticleEffect particle, boolean force, PositionRef pos, int count, Vec3d delta, double speed, int spawnInterval
) implements TransitionConfig {

	public static final MapCodec<SpawnParticleTransitionConfig> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					ParticleTypes.TYPE_CODEC.fieldOf("particle").forGetter(t -> t.particle),
					Codec.BOOL.optionalFieldOf("force", false).forGetter(t -> t.force),
					PositionRefRegistry.CODEC.fieldOf("pos").forGetter(t -> t.pos),
					Codecs.POSITIVE_INT.optionalFieldOf("count", 1).forGetter(t -> t.count),
					Vec3d.CODEC.optionalFieldOf("delta", Vec3d.ZERO).forGetter(t -> t.delta),
					Codec.doubleRange(0, Double.MAX_VALUE).optionalFieldOf("speed", 1.0).forGetter(t -> t.speed),
					Codecs.POSITIVE_INT.optionalFieldOf("spawn_interval", 1).forGetter(t -> t.spawnInterval)
			).apply(instance, SpawnParticleTransitionConfig::new)
	);

	@Override
	public Transition create() {
		return new SpawnParticleTransition(this);
	}

	@Override
	public TransitionConfigType<?> getConfigType() {
		return TransitionConfigRegistry.PARTICLE;
	}
}
