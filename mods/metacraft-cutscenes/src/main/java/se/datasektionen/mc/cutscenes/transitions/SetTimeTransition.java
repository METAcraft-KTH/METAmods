package se.datasektionen.mc.cutscenes.transitions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import se.datasektionen.mc.cutscenes.cutscene.CutsceneInstance;
import se.datasektionen.mc.cutscenes.registry.TransitionConfigRegistry;
import se.datasektionen.mc.cutscenes.registry.TransitionRegistry;
import se.datasektionen.mc.cutscenes.transitions.config.TransitionConfigType;
import se.datasektionen.mc.cutscenes.util.IntervalMap;

public class SetTimeTransition extends InstantTransition {

	public static final MapCodec<SetTimeTransition> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Codec.LONG.fieldOf("time").forGetter(t -> t.time)
			).apply(instance, SetTimeTransition::new)
	);

	private final long time;

	public SetTimeTransition(long time) {
		this.time = time;
	}

	@Override
	public void activate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		cutscene.getCutsceneWorld().setTimeOfDay(time);
		cutscene.getCutsceneWorld().syncTime();
	}

	@Override
	public TransitionType<?> getType() {
		return TransitionRegistry.SET_TIME;
	}

	@Override
	public TransitionConfigType<?> getConfigType() {
		return TransitionConfigRegistry.SET_TIME;
	}
}
