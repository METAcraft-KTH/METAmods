package se.metacraft.cutscenes;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.metacraft.cutscenes.compat.CompatTransitions;
import se.metacraft.cutscenes.entity_ref.EntityRefs;
import se.metacraft.cutscenes.registry.ChunkAreaRegistry;
import se.metacraft.cutscenes.registry.TransitionRegistry;
import se.metacraft.cutscenes.util.cutscene_redirector.CutsceneServerRedirector;

public class Cutscenes implements ModInitializer {
	public static final String NAMESPACE = "metacraft";

	public static final Logger LOGGER = LogManager.getLogger("metacraft-cutscenes");

	@Override
	public void onInitialize() {
		ChunkAreaRegistry.init();
		EntityRefs.init();
		TransitionRegistry.init();
		Commands.init();
		Events.init();
		CompatTransitions.init();
		CutsceneServerRedirector.init();
	}

	public static Identifier getID(String id) {
		return Identifier.fromNamespaceAndPath(NAMESPACE, id);
	}
}
