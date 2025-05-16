package se.metacraft.bosses.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.intprovider.ConstantIntProvider;
import net.minecraft.util.math.intprovider.IntProvider;
import net.minecraft.util.math.random.Random;

import java.util.Optional;

public record StatusEffectEntry(
		RegistryEntry<StatusEffect> effect,
		IntProvider duration,
		IntProvider amplifier,
		boolean ambient,
		Optional<Boolean> showParticles,
		Optional<Boolean> showIcon
) {
	public static final MapCodec<StatusEffectEntry> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					StatusEffect.ENTRY_CODEC.fieldOf("effect").forGetter(a -> a.effect),
					IntProvider.VALUE_CODEC.fieldOf("duration").forGetter(a -> a.duration),
					IntProvider.createValidatingCodec(0, Byte.MAX_VALUE).optionalFieldOf("amplifier", ConstantIntProvider.create(0)).forGetter(a -> a.amplifier),
					Codec.BOOL.optionalFieldOf("ambient", false).forGetter(a -> a.ambient),
					Codec.BOOL.optionalFieldOf("show_particles").forGetter(a -> a.showParticles),
					Codec.BOOL.optionalFieldOf("show_icon").forGetter(a -> a.showIcon)
			).apply(instance, StatusEffectEntry::new)
	);

	public static StatusEffectEntry create(RegistryEntry<StatusEffect> effect, IntProvider duration, IntProvider amplifier) {
		return new StatusEffectEntry(
				effect, duration, amplifier, false,
				Optional.of(true), Optional.of(true)
		);
	}

	public StatusEffectInstance createEffect(Random random) {
		return new StatusEffectInstance(
				effect, duration.get(random), amplifier.get(random),
				ambient, showParticles.or(() -> showIcon).orElse(true),
				showIcon.or(() -> showParticles).orElse(true)
		);
	}

}
