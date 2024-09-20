package se.datasektionen.mc.cutscenes.extension;

import se.datasektionen.mc.cutscenes.cutscene.CutsceneInstance;

import java.util.Optional;

public interface ServerPlayerEntityExtensions {

	void metacraft_cutscenes$setCutscene(CutsceneInstance cutscene);
	boolean metacraft_cutscenes$hasCutscene();
	Optional<CutsceneInstance> metacraft_cutscenes$getCutscene();

	void metacraft$setAllowWrongMovements(boolean showWronglyMovedWarning);
	boolean metacraft$getAllowWrongMovements();

}
