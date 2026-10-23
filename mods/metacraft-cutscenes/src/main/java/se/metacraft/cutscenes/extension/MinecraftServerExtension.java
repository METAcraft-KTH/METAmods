package se.metacraft.cutscenes.extension;

import se.metacraft.cutscenes.CutscenesConfig;

public interface MinecraftServerExtension {

	CutscenesConfig metacraft_cutscenes$getConfig();
	void metacraft_cutscenes$setConfig(CutscenesConfig config);

}
