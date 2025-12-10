package nu.metacraft.cutscenes.transitions;

import nu.metacraft.cutscenes.cutscene.CutsceneInstance;
import nu.metacraft.cutscenes.util.IntervalMap;

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
