package se.datasektionen.mc.cutscenes;

import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.datasektionen.mc.cutscenes.compat.CompatTransitions;
import se.datasektionen.mc.cutscenes.cutscene.CutsceneInstance;
import se.datasektionen.mc.cutscenes.registry.EntityRefRegistry;
import se.datasektionen.mc.cutscenes.registry.PositionRefRegistry;
import se.datasektionen.mc.cutscenes.registry.RotationRefRegistry;
import se.datasektionen.mc.cutscenes.registry.TransitionRegistry;

public class Cutscenes implements ModInitializer {
	public static final String NAMESPACE = "metacraft";

	public static final Logger LOGGER = LogManager.getLogger("metacraft-cutscenes");

	@Override
	public void onInitialize() {
		EntityRefRegistry.init();
		PositionRefRegistry.init();
		RotationRefRegistry.init();
		TransitionRegistry.init();
		CutsceneInstance.init();
		Commands.init();
		Events.init();
		CompatTransitions.init();
	}

	public static Identifier getID(String id) {
		return Identifier.of(NAMESPACE, id);
	}
}
