package nu.metacraft.bosses;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import nu.metacraft.lib.METAcraftLib;
import nu.metacraft.bosses.boss.attacks.AttackRegistry;
import nu.metacraft.bosses.condition.entity_sub_predicate.BossSubPredicates;
import nu.metacraft.bosses.entity.BossEntities;
import nu.metacraft.bosses.item.BossItems;

public class METAcraftBosses implements ModInitializer {

	public static final String NAMESPACE = METAcraftLib.NAMESPACE;
	public static final Logger LOGGER = LogManager.getLogger("metacraft-season-4");

	@Override
	public void onInitialize() {
		// Item models, textures and lang go to clients in the Polymer resource pack.
		PolymerResourcePackUtils.addModAssets("metacraft-bosses");
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
