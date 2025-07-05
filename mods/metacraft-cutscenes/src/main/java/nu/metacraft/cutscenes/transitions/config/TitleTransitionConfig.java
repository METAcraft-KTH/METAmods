package nu.metacraft.cutscenes.transitions.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.dynamic.Codecs;
import nu.metacraft.cutscenes.registry.TransitionConfigRegistry;
import nu.metacraft.cutscenes.transitions.TitleTransition;
import nu.metacraft.cutscenes.transitions.Transition;

import java.util.Optional;

public record TitleTransitionConfig(
		Text title, Optional<Text> subtitle, int fadeIn, int fadeOut,
		boolean stopAtEnd, Optional<Integer> stay
) implements TransitionConfig {

	public static MapCodec<TitleTransitionConfig> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					TextCodecs.CODEC.fieldOf("title").forGetter(TitleTransitionConfig::title),
					TextCodecs.CODEC.optionalFieldOf("subtitle").forGetter(TitleTransitionConfig::subtitle),
					Codecs.NON_NEGATIVE_INT.optionalFieldOf("fade_in", 10).forGetter(TitleTransitionConfig::fadeIn),
					Codecs.NON_NEGATIVE_INT.optionalFieldOf("fade_out", 20).forGetter(TitleTransitionConfig::fadeOut),
					Codec.BOOL.optionalFieldOf("stop_at_end", true).forGetter(TitleTransitionConfig::stopAtEnd),
					Codecs.NON_NEGATIVE_INT.optionalFieldOf("stay").forGetter(TitleTransitionConfig::stay)
			).apply(instance, TitleTransitionConfig::new)
	);

	@Override
	public Transition create() {
		return new TitleTransition(this);
	}

	@Override
	public TransitionConfigType<?> getConfigType() {
		return TransitionConfigRegistry.TITLE;
	}
}
