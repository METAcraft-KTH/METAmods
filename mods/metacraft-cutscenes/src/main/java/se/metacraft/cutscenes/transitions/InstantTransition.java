package se.metacraft.cutscenes.transitions;

import se.metacraft.cutscenes.util.IntervalMap;
import net.minecraft.server.level.ServerPlayer;
import se.metacraft.cutscenes.cutscene.CutsceneInstance;
import se.metacraft.cutscenes.transitions.config.TransitionConfig;

public abstract class InstantTransition implements Transition, TransitionConfig {

	@Override
	public final void tick(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {

	}

	@Override
	public final void deactivate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {

	}

	@Override
	public final void deactivate(ServerPlayer player, CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {}

	@Override
	public final Transition create() {
		return this;
	}
}
