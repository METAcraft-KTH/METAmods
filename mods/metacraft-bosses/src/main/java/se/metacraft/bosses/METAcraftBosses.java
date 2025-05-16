package se.metacraft.bosses;

import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.datasektionen.mc.metacraft_lib.METAcraftLib;
import se.metacraft.bosses.boss.attacks.AttackRegistry;
import se.metacraft.bosses.entity.BossEntities;
import se.metacraft.bosses.item.BossItems;

public class METAcraftBosses implements ModInitializer {

	public static final String NAMESPACE = METAcraftLib.NAMESPACE;
	public static final Logger LOGGER = LogManager.getLogger("metacraft-season-4");

	@Override
	public void onInitialize() {
		AttackRegistry.init();
		BossItems.init();
		BossEntities.init();
	}

	public static Identifier getID(String id) {
		return Identifier.of(NAMESPACE, id);
	}
}
