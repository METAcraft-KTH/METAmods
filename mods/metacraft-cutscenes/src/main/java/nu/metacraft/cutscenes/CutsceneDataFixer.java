package nu.metacraft.cutscenes;

import com.chocohead.mm.api.ClassTinkerers;
import com.mojang.datafixers.DSL;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.datafixer.TypeReferences;

public class CutsceneDataFixer {

	public static final DSL.TypeReference CUTSCENE = TypeReferences.create(
			"metacraft/cutscene"
	);
	public static final DSL.TypeReference SAVED_DATA_MULTIPLAYER_CUTSCENE_MANAGER = TypeReferences.create(
			"metacraft/saved_data/multiplayer_cutscene_manager"
	);

	public static class Types {

		public static final DataFixTypes SAVED_DATA_MULTIPLAYER_CUTSCENE_MANAGER = ClassTinkerers.getEnum(
				DataFixTypes.class, CutsceneEnums.SAVED_DATA_MULTIPLAYER_CUTSCENE_MANAGER
		);

	}
}
