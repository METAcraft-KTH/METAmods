package se.metacraft.core;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import se.metacraft.core.environment_attributes.METAcraftAttributeTypes;
import se.metacraft.core.status_effects.METAcraftEffects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.metacraft.core.block.METAcraftBlocks;
import se.metacraft.core.compat.CompatInit;
import se.metacraft.core.entity.METAcraftEntities;
import se.metacraft.core.gamerules.METAcraftGameRules;
import se.metacraft.core.item.METAcraftCreativeTab;
import se.metacraft.core.item.SpawnEggsCreativeTab;
import se.metacraft.core.item.METAcraftItems;
import se.metacraft.core.item.components.METAcraftComponents;
import se.metacraft.core.music.MusicTimerTracker;
import se.metacraft.core.portal.PortalTargetRegistry;
import se.metacraft.core.preferences.Preference;
import se.metacraft.core.registry.EntityRefRegistry;
import se.metacraft.core.registry.PositionRefRegistry;
import se.metacraft.core.registry.RotationRefRegistry;
import se.metacraft.lib.METAcraftLib;

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
		METAcraftCreativeTab.init();
		SpawnEggsCreativeTab.init();
		METAcraftAttributeTypes.init();
		Commands.init();
		Events.init();
		CompatInit.init();
		MusicTimerTracker.init();

		PolymerResourcePackUtils.addModAssets(MODID);
		PolymerResourcePackUtils.markAsRequired();
	}

	public static Identifier getID(String id) {
		return Identifier.fromNamespaceAndPath(NAMESPACE, id);
	}
}
