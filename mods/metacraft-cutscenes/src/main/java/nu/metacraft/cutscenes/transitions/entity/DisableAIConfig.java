package nu.metacraft.cutscenes.transitions.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import nu.metacraft.core.entity_ref.EntityRef;
import nu.metacraft.core.registry.EntityRefRegistry;
import nu.metacraft.cutscenes.registry.TransitionConfigRegistry;
import nu.metacraft.cutscenes.transitions.Transition;
import nu.metacraft.cutscenes.transitions.config.TransitionConfig;
import nu.metacraft.cutscenes.transitions.config.TransitionConfigType;

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
