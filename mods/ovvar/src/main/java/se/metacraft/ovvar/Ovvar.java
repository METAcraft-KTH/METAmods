package se.metacraft.ovvar;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import se.metacraft.ovvar.compat.danse.DanseCompat;
import se.metacraft.ovvar.compat.danse.DanseHooks;
import se.metacraft.ovvar.content.Chapter;
import se.metacraft.ovvar.content.MobOvvar;
import se.metacraft.ovvar.content.ModContent;
import se.metacraft.ovvar.content.PatchLoot;
import se.metacraft.ovvar.content.enchantment.OvveEnchantments;
import se.metacraft.ovvar.content.Patches;
import se.metacraft.ovvar.content.entity.PunkphozEntities;
import se.metacraft.ovvar.content.item.OvveFeet;
import se.metacraft.ovvar.content.item.OvveTop;
import se.metacraft.ovvar.pack.Combos;
import se.metacraft.ovvar.pack.ShaderPatcher;
import se.metacraft.ovvar.pack.WardrobeArt;
import se.metacraft.ovvar.sewing.StandDisplays;
import se.metacraft.ovvar.sewing.StandSewing;
import se.metacraft.ovvar.sewing.StashSession;
import se.metacraft.ovvar.sewing.WardrobeMannequin;
import se.metacraft.ovvar.store.Wardrobes;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ovvar: student overalls with sewn-on patches, for vanilla clients via Polymer. Everything the
 * client draws is a pre-generated equipment asset, so the pack is required — without it a garment
 * is a leather piece wearing the wrong skin, which is exactly the kind of quiet wrongness we refuse.
 */
public class Ovvar implements ModInitializer {
	public static final String MOD_ID = "ovvar";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	/**
	 * Is this the datagen run rather than a server? Anything that reads a generated asset has to
	 * keep out of the run that writes it — {@link ModContent} checks the
	 * same property before demanding the assets exist.
	 */
	public static final boolean DATAGEN = System.getProperty("fabric-api.datagen") != null;

	@Override
	public void onInitialize() {
		OvvarConfig.get();
		ModContent.register();
		PunkphozEntities.init();
		PatchLoot.init();
		OvveEnchantments.init();
		MobOvvar.init();
		OvveTop.init();
		OvveFeet.init();
		StandSewing.init();
		StandDisplays.init();
		Combos.init();
		WardrobeArt.init();
		Wardrobes.init();
		// After Combos' and Wardrobes' own SERVER_STARTING listeners (KNOWN loaded, store open), and
		// before the pack builds at SERVER_STARTED.
		ServerLifecycleEvents.SERVER_STARTING.register(s -> {
			int added = Combos.prebuild(Wardrobes.storedDesigns(Duration.ofSeconds(20)));
			LOGGER.info("[ovvar] pre-built {} stored design combination(s)", added);
		});
		// Every mod's items are registered by now, so a pool item from a missing mod is logged at startup.
		ServerLifecycleEvents.SERVER_STARTED.register(s -> PunkphozConfig.poolOnOffer());
		StashSession.init();
		WardrobeMannequin.init();
		ModCommands.init();
		ShaderPatcher.init();
		// Gestures in an ovve. The only reference to the compat package, behind the only check that
		// Danse is here; nothing else in ovvar knows the mod exists.
		if (DanseHooks.active()) DanseCompat.init();

		PolymerResourcePackUtils.addModAssets(MOD_ID);
		PolymerResourcePackUtils.markAsRequired();

		LOGGER.info("[{}] {} chapter(s), {} patch(es)", MOD_ID, Chapter.values().length, Patches.all().size());
	}
}
