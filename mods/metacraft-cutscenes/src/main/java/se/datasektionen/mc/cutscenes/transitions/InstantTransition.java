package se.datasektionen.mc.cutscenes.transitions;

import net.minecraft.server.network.ServerPlayerEntity;
import se.datasektionen.mc.cutscenes.util.IntervalMap;
import se.datasektionen.mc.cutscenes.cutscene.CutsceneInstance;
import se.datasektionen.mc.cutscenes.transitions.config.TransitionConfig;

public abstract class InstantTransition implements Transition, TransitionConfig {

	@Override
	public final void tick(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {

	}

	@Override
	public final void deactivate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {

	}

	@Override
	public final void deactivate(ServerPlayerEntity player, CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {}

	@Override
	public final Transition create() {
		return this;
	}
}
