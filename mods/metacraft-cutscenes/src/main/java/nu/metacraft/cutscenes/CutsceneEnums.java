package nu.metacraft.cutscenes;

import com.chocohead.mm.api.ClassTinkerers;
import com.mojang.datafixers.DSL;
import nu.metacraft.lib.util.EarlyClassNames;

public class CutsceneEnums implements Runnable {

	protected static final String SAVED_DATA_MULTIPLAYER_CUTSCENE_MANAGER = "METACRAFT_SAVED_DATA_MULTIPLAYER_CUTSCENE_MANAGER";

	@Override
	public void run() {
		ClassTinkerers.enumBuilder(
				EarlyClassNames.DATA_FIX_TYPES,
				DSL.TypeReference.class
		).addEnum(
				SAVED_DATA_MULTIPLAYER_CUTSCENE_MANAGER,
				CutsceneDataFixer.SAVED_DATA_MULTIPLAYER_CUTSCENE_MANAGER
		).build();
	}
}
