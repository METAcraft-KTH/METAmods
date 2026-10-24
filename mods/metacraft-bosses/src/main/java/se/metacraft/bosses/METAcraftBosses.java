package se.metacraft.bosses;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.metacraft.lib.METAcraftLib;
import se.metacraft.bosses.boss.attacks.AttackRegistry;
import se.metacraft.bosses.condition.entity_sub_predicate.BossSubPredicates;
import se.metacraft.bosses.entity.BossEntities;
import se.metacraft.bosses.item.BossItems;

public class METAcraftBosses implements ModInitializer {

	public static final String NAMESPACE = METAcraftLib.NAMESPACE;
	public static final String MODID = "metacraft-bosses";
	public static final Logger LOGGER = LogManager.getLogger(MODID);

	@Override
	public void onInitialize() {
		// Item models, textures and lang go to clients in the Polymer resource pack.
		PolymerResourcePackUtils.addModAssets(MODID);
		PolymerResourcePackUtils.markAsRequired();
		AttackRegistry.init();
		BossItems.init();
		BossEntities.init();
		BossSubPredicates.init();
	}

	public static Identifier getID(String id) {
		return Identifier.fromNamespaceAndPath(NAMESPACE, id);
	}
}
