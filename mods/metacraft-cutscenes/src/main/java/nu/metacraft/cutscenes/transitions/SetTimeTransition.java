package nu.metacraft.cutscenes.transitions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import nu.metacraft.cutscenes.cutscene.CutsceneInstance;
import nu.metacraft.cutscenes.registry.TransitionConfigRegistry;
import nu.metacraft.cutscenes.registry.TransitionRegistry;
import nu.metacraft.cutscenes.transitions.config.TransitionConfigType;
import nu.metacraft.cutscenes.util.IntervalMap;

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
		cutscene.getCutsceneWorld().setDayTime(time);
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
