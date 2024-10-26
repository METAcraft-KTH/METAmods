package se.datasektionen.mc.cutscenes;

import com.chocohead.mm.api.ClassTinkerers;
import com.mojang.datafixers.DSL;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;

public class CutsceneEnums implements Runnable {

	protected static final String SAVED_DATA_MULTIPLAYER_CUTSCENE_MANAGER = "METACRAFT_SAVED_DATA_MULTIPLAYER_CUTSCENE_MANAGER";

	@Override
	public void run() {
		MappingResolver remapper = FabricLoader.getInstance().getMappingResolver();

		ClassTinkerers.enumBuilder(
				remapper.mapClassName(
						"named",
						"net.minecraft.datafixer.DataFixTypes"
				),
				DSL.TypeReference.class
		).addEnum(
				SAVED_DATA_MULTIPLAYER_CUTSCENE_MANAGER,
				CutsceneDataFixer.SAVED_DATA_MULTIPLAYER_CUTSCENE_MANAGER
		).build();
	}
}
