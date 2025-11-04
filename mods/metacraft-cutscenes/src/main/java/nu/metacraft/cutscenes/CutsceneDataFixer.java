package nu.metacraft.cutscenes;

import com.chocohead.mm.api.ClassTinkerers;
import com.mojang.datafixers.DSL;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.datafix.fixes.References;

public class CutsceneDataFixer {

	public static final DSL.TypeReference CUTSCENE = References.reference(
			"metacraft/cutscene"
	);
	public static final DSL.TypeReference SAVED_DATA_MULTIPLAYER_CUTSCENE_MANAGER = References.reference(
			"metacraft/saved_data/multiplayer_cutscene_manager"
	);

	public static class Types {

		public static final DataFixTypes SAVED_DATA_MULTIPLAYER_CUTSCENE_MANAGER = ClassTinkerers.getEnum(
				DataFixTypes.class, CutsceneEnums.SAVED_DATA_MULTIPLAYER_CUTSCENE_MANAGER
		);

	}
}
