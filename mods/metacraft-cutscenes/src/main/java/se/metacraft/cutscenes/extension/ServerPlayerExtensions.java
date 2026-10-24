package se.metacraft.cutscenes.extension;

import se.metacraft.cutscenes.cutscene.CutsceneInstance;

import java.util.Optional;

public interface ServerPlayerExtensions {

	void metacraft_cutscenes$setCutscene(CutsceneInstance cutscene);
	boolean metacraft_cutscenes$hasCutscene();
	Optional<CutsceneInstance> metacraft_cutscenes$getCutscene();

	void metacraft$setAllowWrongMovements(boolean showWronglyMovedWarning);
	boolean metacraft$getAllowWrongMovements();

}
