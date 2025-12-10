package nu.metacraft.cutscenes.transitions;

import nu.metacraft.cutscenes.util.IntervalMap;
import net.minecraft.server.level.ServerPlayer;
import nu.metacraft.cutscenes.cutscene.CutsceneInstance;
import nu.metacraft.cutscenes.transitions.config.TransitionConfig;

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
