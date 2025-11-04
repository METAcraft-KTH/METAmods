package nu.metacraft.cutscenes;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import nu.metacraft.cutscenes.compat.CompatTransitions;
import nu.metacraft.cutscenes.cutscene.CutsceneInstance;
import nu.metacraft.cutscenes.entity_ref.EntityRefs;
import nu.metacraft.cutscenes.registry.ChunkAreaRegistry;
import nu.metacraft.cutscenes.registry.TransitionRegistry;
import nu.metacraft.cutscenes.util.cutscene_redirector.CutsceneServerRedirector;

public class Cutscenes implements ModInitializer {
	public static final String NAMESPACE = "metacraft";

	public static final Logger LOGGER = LogManager.getLogger("metacraft-cutscenes");

	@Override
	public void onInitialize() {
		ChunkAreaRegistry.init();
		EntityRefs.init();
		TransitionRegistry.init();
		CutsceneInstance.init();
		Commands.init();
		Events.init();
		CompatTransitions.init();
		CutsceneServerRedirector.init();
	}

	public static ResourceLocation getID(String id) {
		return ResourceLocation.fromNamespaceAndPath(NAMESPACE, id);
	}
}
