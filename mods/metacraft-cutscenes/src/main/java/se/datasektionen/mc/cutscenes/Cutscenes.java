package se.datasektionen.mc.cutscenes;

import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.datasektionen.mc.cutscenes.compat.CompatTransitions;
import se.datasektionen.mc.cutscenes.cutscene.CutsceneInstance;
import se.datasektionen.mc.cutscenes.registry.*;
import se.datasektionen.mc.cutscenes.util.cutscene_redirector.CutsceneServerRedirector;

public class Cutscenes implements ModInitializer {
	public static final String NAMESPACE = "metacraft";

	public static final Logger LOGGER = LogManager.getLogger("metacraft-cutscenes");

	@Override
	public void onInitialize() {
		ChunkAreaRegistry.init();
		EntityRefRegistry.init();
		PositionRefRegistry.init();
		RotationRefRegistry.init();
		TransitionRegistry.init();
		CutsceneInstance.init();
		Commands.init();
		Events.init();
		CompatTransitions.init();
		CutsceneServerRedirector.init();
	}

	public static Identifier getID(String id) {
		return Identifier.of(NAMESPACE, id);
	}
}
