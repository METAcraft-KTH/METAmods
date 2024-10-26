package se.datasektionen.mc.cutscenes.transitions.config;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.dynamic.Codecs;
import se.datasektionen.mc.cutscenes.registry.TransitionConfigRegistry;
import se.datasektionen.mc.cutscenes.transitions.TitleTransition;
import se.datasektionen.mc.cutscenes.transitions.Transition;

import java.util.Optional;

public record TitleTransitionConfig(Text title, Optional<Text> subtitle, int fadeIn, int fadeOut) implements TransitionConfig {

	public static MapCodec<TitleTransitionConfig> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					TextCodecs.CODEC.fieldOf("title").forGetter(TitleTransitionConfig::title),
					TextCodecs.CODEC.optionalFieldOf("subtitle").forGetter(TitleTransitionConfig::subtitle),
					Codecs.NON_NEGATIVE_INT.optionalFieldOf("fade_in", 10).forGetter(TitleTransitionConfig::fadeIn),
					Codecs.NON_NEGATIVE_INT.optionalFieldOf("fade_out", 20).forGetter(TitleTransitionConfig::fadeOut)
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
