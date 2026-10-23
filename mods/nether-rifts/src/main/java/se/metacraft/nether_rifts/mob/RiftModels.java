package se.metacraft.nether_rifts.mob;

import de.tomalbrc.bil.core.holder.base.AbstractAnimationHolder;
import de.tomalbrc.bil.core.model.Model;
import de.tomalbrc.bil.file.loader.BbModelLoader;
import net.minecraft.world.entity.LivingEntity;
import se.metacraft.nether_rifts.NetherRifts;
import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * The rift mobs' Blockbench models (the artist's, brought into /model/nether_rifts/ by tools/import_drummer.py and
 * tools/import_ghast.py), loaded once: BIL doesn't cache models, and they must exist before Polymer builds the
 * resource pack.
 */
public final class RiftModels {

	/** Each loaded model's name; filled before the models below. */
	private static final Map<Model, String> NAMES = new IdentityHashMap<>();

	public static final Model WAR_DRUMMER = load("war_drummer");
	public static final Model WAR_GHAST = load("war_ghast");

	/**
	 * The red a mob flashes while hurt or dying. Vanilla tints its own models; BIL's bones are item displays it
	 * doesn't touch, so a BIL mob tints them itself ({@link #hurtTint}).
	 */
	public static final int HURT_COLOR = 0xFF7E7E;

	private RiftModels() {}

	/** Tints a BIL mob's bones red while it's hurt or dying, as vanilla does its own mobs. Cheap to call every tick. */
	public static void hurtTint(LivingEntity mob, AbstractAnimationHolder holder) {
		holder.setColor(tint(mob));
	}

	/** The colour a mob's bones take now: {@link #HURT_COLOR} while it's hurt or dying, else white (-1). */
	public static int tint(LivingEntity mob) {
		return mob.hurtTime > 0 || mob.deathTime > 0 ? HURT_COLOR : -1;
	}

	/** A model's name (its file under /model/nether_rifts/), or null for one not loaded here. */
	@Nullable
	public static String name(Model model) {
		return NAMES.get(model);
	}

	/** Forces the models to load during mod init. */
	public static void init() {
	}

	private static Model load(String name) {
		var model = BbModelLoader.load(NetherRifts.getID(name));
		NAMES.put(model, name);
		return model;
	}
}
