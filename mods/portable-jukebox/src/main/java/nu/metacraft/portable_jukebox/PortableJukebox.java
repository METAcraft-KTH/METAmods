package nu.metacraft.portable_jukebox;

import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import nu.metacraft.portable_jukebox.compat.CompatInit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import nu.metacraft.portable_jukebox.block.Blocks;
import nu.metacraft.portable_jukebox.entity.Entities;
import nu.metacraft.portable_jukebox.item.Items;

public class PortableJukebox implements ModInitializer {

	public static final String NAMESPACE = "portable_jukebox";

	public static final Logger LOGGER = LogManager.getLogger("portable-jukebox");

	@Override
	public void onInitialize() {
		Items.init();
		Blocks.init();
		Entities.init();
		CompatInit.init();
	}

	public static Identifier getID(String id) {
		return Identifier.of(NAMESPACE, id);
	}
}
