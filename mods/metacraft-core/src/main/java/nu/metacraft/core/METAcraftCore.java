package nu.metacraft.core;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import nu.metacraft.core.environment_attributes.METAcraftAttributeTypes;
import nu.metacraft.core.status_effects.METAcraftEffects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import nu.metacraft.core.block.METAcraftBlocks;
import nu.metacraft.core.compat.CompatInit;
import nu.metacraft.core.entity.METAcraftEntities;
import nu.metacraft.core.gamerules.METAcraftGameRules;
import nu.metacraft.core.item.METAcraftItems;
import nu.metacraft.core.item.components.METAcraftComponents;
import nu.metacraft.core.music.MusicTimerTracker;
import nu.metacraft.core.portal.PortalTargetRegistry;
import nu.metacraft.core.preferences.Preference;
import nu.metacraft.core.registry.EntityRefRegistry;
import nu.metacraft.core.registry.PositionRefRegistry;
import nu.metacraft.core.registry.RotationRefRegistry;
import nu.metacraft.lib.METAcraftLib;

public class METAcraftCore implements ModInitializer {

	public static final String NAMESPACE = METAcraftLib.NAMESPACE;
	public static final String MODID = "metacraft-core";
	public static final Logger LOGGER = LogManager.getLogger(MODID);

	@Override
	public void onInitialize() {
		EntityRefRegistry.init();
		PositionRefRegistry.init();
		RotationRefRegistry.init();
		PortalTargetRegistry.init();
		Preference.init();
		METAcraftEffects.init();
		METAcraftGameRules.init();
		METAcraftComponents.init();
		METAcraftBlocks.init();
		METAcraftEntities.init();
		METAcraftItems.init();
		METAcraftAttributeTypes.init();
		Commands.init();
		Events.init();
		CompatInit.init();
		MusicTimerTracker.init();
	}

	public static Identifier getID(String id) {
		return Identifier.fromNamespaceAndPath(NAMESPACE, id);
	}
}
