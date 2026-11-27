package se.metacraft.portable_jukebox;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import se.metacraft.portable_jukebox.compat.CompatInit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.metacraft.portable_jukebox.block.Blocks;
import se.metacraft.portable_jukebox.entity.Entities;
import se.metacraft.portable_jukebox.item.Items;

public class Boombox implements ModInitializer {

	public static final String NAMESPACE = "portable_jukebox";

	public static final Logger LOGGER = LogManager.getLogger("portable-jukebox");

	@Override
	public void onInitialize() {
		Items.init();
		Blocks.init();
		Entities.init();
		CompatInit.init();
		PolymerResourcePackUtils.addModAssets("portable-jukebox");
		PolymerResourcePackUtils.markAsRequired();
	}

	public static Identifier getID(String id) {
		return Identifier.fromNamespaceAndPath(NAMESPACE, id);
	}
}
