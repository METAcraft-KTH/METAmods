package metacraft.ovvar.compat.danse;

import metacraft.ovvar.content.OvveFeet;
import metacraft.ovvar.pack.Combos;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Gesture etiquette: the few places ovvar has to hold still while Danse is animating a stand-in.
 *
 * <p>Danse plays a gesture by hiding the real player — an empty equipment packet to every watcher
 * and the invisible flag — and animating a puppet of item displays built from the equipment it
 * took <em>once</em>, at the start. Ovvar re-sends equipment on a schedule of its own
 * ({@link Combos#packLoaded} after a pack load, {@link OvveFeet} when a viewer's cuff dye changes),
 * and either of those would re-dress the invisible player in the middle of the gesture. So both
 * ask here first.
 *
 * <p>It is also the gate for the rest of the package. Danse types are named only here (inside
 * {@link Impl}), in {@link DanseCompat}, {@link DanseLayers} and {@link DanseModels}, none of which
 * load until {@link #active()} has said Danse is here, so a server without Danse never verifies a
 * class that mentions it. The body layers need <em>our fork</em> of Danse
 * (<a href="https://github.com/Froosty11/danse">Froosty11/danse</a>); upstream Danse has the same mod id but not the API, and gets the
 * etiquette only ({@link #mode}). The system property {@code ovvar.danse.compat=false} turns the
 * whole layer off (this and {@link DanseMixinPlugin} read the same flag), which is how the "before"
 * screenshot is taken.
 */
public final class DanseHooks {
	private DanseHooks() {}

	/** {@code -Dovvar.danse.compat=false} disables the compat layer: hooks and mixins alike. */
	public static final String PROPERTY = "ovvar.danse.compat";

	/** How much of the compat layer runs. */
	public enum Mode {
		/** No Danse, or switched off. */
		OFF,
		/** Danse without our fork's body-layer API: hold still during gestures, draw nothing. */
		ETIQUETTE,
		/** Our fork: the etiquette, and the ovve drawn on the stand-in as body layers. */
		LAYERS
	}

	/** The body-layer API our fork adds; looked up as a resource so nothing is loaded to ask. */
	private static final String BODY_LAYERS = "de/tomalbrc/danse/api/BodyLayers.class";

	private static final Mode MODE = mode(
			!"false".equalsIgnoreCase(System.getProperty(PROPERTY, "true")),
			FabricLoader.getInstance().isModLoaded("danse"),
			DanseHooks.class.getClassLoader().getResource(BODY_LAYERS) != null);

	private static final boolean ACTIVE = MODE != Mode.OFF;

	public static Mode mode(boolean enabled, boolean danseLoaded, boolean bodyLayers) {
		if (!enabled || !danseLoaded) return Mode.OFF;
		return bodyLayers ? Mode.LAYERS : Mode.ETIQUETTE;
	}

	/** Is Danse loaded and the compat layer switched on? */
	public static boolean active() {
		return ACTIVE;
	}

	/** Is it our fork, so the ovve can be drawn on the stand-in? */
	public static boolean layers() {
		return MODE == Mode.LAYERS;
	}

	/**
	 * Is this entity in the middle of a Danse gesture — that is, is Danse drawing a stand-in for
	 * them and holding their real body invisible? While that is true nothing of ours may send them
	 * equipment.
	 */
	public static boolean gesturing(LivingEntity entity) {
		return ACTIVE && entity instanceof ServerPlayer player && Impl.gesturing(player);
	}

	/**
	 * A gesture has ended (from the mixin on Danse's {@code onStop}): put the wearer's ovve back on
	 * every tracker's screen.
	 *
	 * <p>Danse's own {@code onStop} re-sends the player's <em>raw</em> equipment, which for a wearer
	 * with no boots means an empty feet slot — and the feet slot is where ovvar smuggles the legs'
	 * second dye channel, the virtual cuffs. Ovvar's cuff cache still believes that viewer has them,
	 * so without this the leg patches in the boots channel would stay dark until something else
	 * changed. Forgetting the wearer makes the next tick re-send the cuffs to everyone tracking
	 * them; the equipment re-send puts the ovve and the companion top back with the right assets.
	 */
	public static void gestureEnded(ServerPlayer player) {
		if (!ACTIVE) return;
		OvveFeet.forget(player);
		Combos.resendEquipment(player);
	}

	/** Danse's gesture registry; loaded lazily, behind {@link #ACTIVE}. */
	private static final class Impl {
		private Impl() {}

		static boolean gesturing(ServerPlayer player) {
			return de.tomalbrc.danse.GestureController.GESTURE_CAMS.containsKey(player.getUUID());
		}
	}
}
