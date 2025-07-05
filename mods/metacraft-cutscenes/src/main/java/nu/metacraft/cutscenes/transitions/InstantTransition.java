package nu.metacraft.cutscenes.transitions;

import net.minecraft.server.network.ServerPlayerEntity;
import nu.metacraft.cutscenes.util.IntervalMap;
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
	public final void deactivate(ServerPlayerEntity player, CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {}

	@Override
	public final Transition create() {
		return this;
	}
}
