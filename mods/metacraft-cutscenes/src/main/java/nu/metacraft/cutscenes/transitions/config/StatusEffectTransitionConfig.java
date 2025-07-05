package nu.metacraft.cutscenes.transitions.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.dynamic.Codecs;
import nu.metacraft.cutscenes.registry.TransitionConfigRegistry;
import nu.metacraft.cutscenes.transitions.StatusEffectTransition;
import nu.metacraft.cutscenes.transitions.Transition;

public record StatusEffectTransitionConfig(RegistryEntry<StatusEffect> effect, int amplifier, int duration, int resetInterval) implements TransitionConfig {

	public static final MapCodec<StatusEffectTransitionConfig> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					StatusEffect.ENTRY_CODEC.fieldOf("id").forGetter(StatusEffectTransitionConfig::effect),
					Codecs.UNSIGNED_BYTE.optionalFieldOf("amplifier", 0).forGetter(StatusEffectTransitionConfig::amplifier),
					Codec.INT.optionalFieldOf("duration", 0).forGetter(StatusEffectTransitionConfig::duration),
					Codecs.POSITIVE_INT.optionalFieldOf("reset_interval", 1).forGetter(StatusEffectTransitionConfig::resetInterval)
			).apply(instance, StatusEffectTransitionConfig::new)
	);

	@Override
	public Transition create() {
		return new StatusEffectTransition(this);
	}

	@Override
	public TransitionConfigType<?> getConfigType() {
		return TransitionConfigRegistry.STATUS_EFFECT;
	}
}
