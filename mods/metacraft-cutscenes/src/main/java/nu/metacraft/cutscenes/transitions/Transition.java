package nu.metacraft.cutscenes.transitions;

import nu.metacraft.cutscenes.util.IntervalMap;
import net.minecraft.server.level.ServerPlayer;
import nu.metacraft.cutscenes.cutscene.CutsceneInstance;

public interface Transition {

	void activate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval);
	default void activate(ServerPlayer player, CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {}
	void tick(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval);
	void deactivate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval);
	default void deactivate(ServerPlayer player, CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {}
	default void copyFromPreviousCutscene(CutsceneInstance prev, CutsceneInstance current, IntervalMap.Interval<Transition> interval) {}

	TransitionType<?> getType();

}
