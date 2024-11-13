package se.datasektionen.mc.portable_jukebox.item;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import se.datasektionen.mc.portable_jukebox.item.components.Components;
import se.datasektionen.mc.portable_jukebox.PortableJukebox;

import java.util.function.Function;

public class Items {

	public static final Item PORTABLE_JUKEBOX = register(
			"portable_jukebox", PortableJukeboxItem::new,
			new Item.Settings().maxCount(1).useBlockPrefixedTranslationKey()
	);

	public static void init() {
		Components.init();
	}

	private static <T extends Item> T register(String id, Function<Item.Settings, T> item, Item.Settings settings) {
		var key = RegistryKey.of(RegistryKeys.ITEM, PortableJukebox.getID(id));
		return Registry.register(Registries.ITEM, key, item.apply(settings.registryKey(key)));
	}

}
