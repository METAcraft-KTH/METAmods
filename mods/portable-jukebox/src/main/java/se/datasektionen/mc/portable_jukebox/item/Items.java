package se.datasektionen.mc.portable_jukebox.item;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import se.datasektionen.mc.portable_jukebox.item.components.Components;
import se.datasektionen.mc.portable_jukebox.PortableJukebox;

public class Items {

	public static final Item PORTABLE_JUKEBOX = register("portable_jukebox", new PortableJukeboxItem(
			new Item.Settings().maxCount(1)
	));

	public static void init() {
		Components.init();
	}

	private static <T extends Item> T register(String id, T item) {
		return Registry.register(Registries.ITEM, PortableJukebox.getID(id), item);
	}

}
