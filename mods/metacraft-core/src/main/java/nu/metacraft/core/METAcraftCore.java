package nu.metacraft.core;

import net.fabricmc.api.ModInitializer;
import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import nu.metacraft.core.block.METAcraftBlocks;
import nu.metacraft.core.compat.CompatInit;
import nu.metacraft.core.entity.METAcraftEntities;
import nu.metacraft.core.gamerules.METAcraftGameRules;
import nu.metacraft.core.item.METAcraftItems;
import nu.metacraft.core.item.components.METAcraftComponents;
import nu.metacraft.core.mixin.AccessorPolymerItemUtils;
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
		METAcraftGameRules.init();
		METAcraftComponents.init();
		METAcraftBlocks.init();
		METAcraftEntities.init();
		METAcraftItems.init();
		Commands.init();
		Events.init();
		CompatInit.init();
		MusicTimerTracker.init();

		//Fix for crossbows not working properly with polymer items.
		var oldComponents = AccessorPolymerItemUtils.getComponentsToCopy();
		var newComponents = new ComponentType<?>[oldComponents.length+1];
		System.arraycopy(oldComponents, 0, newComponents, 0, oldComponents.length);
		newComponents[oldComponents.length] = DataComponentTypes.CHARGED_PROJECTILES;
		AccessorPolymerItemUtils.setComponentsToCopy(newComponents);
	}

	public static Identifier getID(String id) {
		return Identifier.of(NAMESPACE, id);
	}
}
