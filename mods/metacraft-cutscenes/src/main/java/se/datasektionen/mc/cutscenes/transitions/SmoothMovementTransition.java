package se.datasektionen.mc.cutscenes.transitions;

import se.datasektionen.mc.cutscenes.util.IntervalMap;
import se.datasektionen.mc.cutscenes.util.Target;
import se.datasektionen.mc.cutscenes.cutscene.CutsceneInstance;

import java.util.TimerTask;

public interface SmoothMovementTransition {

	Target getStart();

	Target getEnd();

	default TimerTask getSuperTick(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		return null;
	}

}
