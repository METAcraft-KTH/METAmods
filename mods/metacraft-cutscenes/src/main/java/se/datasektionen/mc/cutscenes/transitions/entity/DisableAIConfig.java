package se.datasektionen.mc.cutscenes.transitions.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import se.datasektionen.mc.cutscenes.entity_ref.EntityRef;
import se.datasektionen.mc.cutscenes.registry.EntityRefRegistry;
import se.datasektionen.mc.cutscenes.registry.TransitionConfigRegistry;
import se.datasektionen.mc.cutscenes.transitions.Transition;
import se.datasektionen.mc.cutscenes.transitions.config.TransitionConfig;
import se.datasektionen.mc.cutscenes.transitions.config.TransitionConfigType;

public record DisableAIConfig(EntityRef entity, boolean onlySensors) implements TransitionConfig {

	public static final MapCodec<DisableAIConfig> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					EntityRefRegistry.CODEC.fieldOf("entity").forGetter(DisableAIConfig::entity),
					Codec.BOOL.fieldOf("only_sensors").forGetter(DisableAIConfig::onlySensors)
			).apply(instance, DisableAIConfig::new)
	);

	@Override
	public Transition create() {
		return new DisableAI(this);
	}

	@Override
	public TransitionConfigType<?> getConfigType() {
		return TransitionConfigRegistry.DISABLE_ENTITY_AI;
	}
}
