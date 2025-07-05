package nu.metacraft.cutscenes.extension;

import nu.metacraft.cutscenes.CutscenesConfig;

public interface MinecraftServerExtension {

	CutscenesConfig metacraft_cutscenes$getConfig();
	void metacraft_cutscenes$setConfig(CutscenesConfig config);

}
