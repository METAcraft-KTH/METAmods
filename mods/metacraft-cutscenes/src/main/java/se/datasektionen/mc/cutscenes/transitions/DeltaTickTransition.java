package se.datasektionen.mc.cutscenes.transitions;

import se.datasektionen.mc.cutscenes.cutscene.CutsceneInstance;
import se.datasektionen.mc.cutscenes.util.IntervalMap;

import java.util.TimerTask;

public interface DeltaTickTransition {

	void setProgress(long time);

	long getProgress();

	void tickDelta(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval, float delta);

	default int getInterval() {
		return 33;
	}

	void setTask(TimerTask task);

	TimerTask getTask();

}
