package metacraft.ovvar.compat.danse;

import de.tomalbrc.danse.api.BodyLayers;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import metacraft.ovvar.Ovvar;

/**
 * The compat layer's one entry point, called from {@link Ovvar}'s init behind
 * {@link DanseHooks#active()}: with our Danse fork, registers the body-layer provider and adds the
 * layer models to the pack; with upstream Danse, only says why the stand-in will not wear the ovve.
 */
public final class DanseCompat {
	private DanseCompat() {}

	public static void init() {
		if (!DanseHooks.layers()) {
			Ovvar.LOGGER.warn("[ovvar] Danse is here but not our fork (no body-layer API): gestures hold still for "
					+ "the ovve but will not show it. The fork: https://github.com/Froosty11/danse");
			return;
		}
		BodyLayers.register(DanseLayers.INSTANCE);
		PolymerResourcePackUtils.RESOURCE_PACK_CREATION_EVENT.register(builder -> DanseModels.write(builder::addData));
		Ovvar.LOGGER.info("[ovvar] Danse is here: gestures will wear the ovve");
	}
}
