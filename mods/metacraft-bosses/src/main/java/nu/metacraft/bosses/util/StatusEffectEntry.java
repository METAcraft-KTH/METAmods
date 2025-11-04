package nu.metacraft.bosses.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

public record StatusEffectEntry(
		Holder<MobEffect> effect,
		IntProvider duration,
		IntProvider amplifier,
		boolean ambient,
		Optional<Boolean> showParticles,
		Optional<Boolean> showIcon
) {
	public static final MapCodec<StatusEffectEntry> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					MobEffect.CODEC.fieldOf("effect").forGetter(a -> a.effect),
					IntProvider.CODEC.fieldOf("duration").forGetter(a -> a.duration),
					IntProvider.codec(0, Byte.MAX_VALUE).optionalFieldOf("amplifier", ConstantInt.of(0)).forGetter(a -> a.amplifier),
					Codec.BOOL.optionalFieldOf("ambient", false).forGetter(a -> a.ambient),
					Codec.BOOL.optionalFieldOf("show_particles").forGetter(a -> a.showParticles),
					Codec.BOOL.optionalFieldOf("show_icon").forGetter(a -> a.showIcon)
			).apply(instance, StatusEffectEntry::new)
	);

	public static StatusEffectEntry create(Holder<MobEffect> effect, IntProvider duration, IntProvider amplifier) {
		return new StatusEffectEntry(
				effect, duration, amplifier, false,
				Optional.of(true), Optional.of(true)
		);
	}

	public MobEffectInstance createEffect(RandomSource random) {
		return new MobEffectInstance(
				effect, duration.sample(random), amplifier.sample(random),
				ambient, showParticles.or(() -> showIcon).orElse(true),
				showIcon.or(() -> showParticles).orElse(true)
		);
	}

}
