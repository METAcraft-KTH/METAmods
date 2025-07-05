package nu.metacraft.bosses;

import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
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
		AttackRegistry.init();
		BossItems.init();
		BossEntities.init();
		BossSubPredicates.init();
	}

	public static Identifier getID(String id) {
		return Identifier.of(NAMESPACE, id);
	}
}
