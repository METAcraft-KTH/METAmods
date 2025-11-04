package nu.metacraft.portable_jukebox.item;

import nu.metacraft.portable_jukebox.item.components.Components;
import nu.metacraft.portable_jukebox.PortableJukebox;

import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

public class Items {

	public static final Item PORTABLE_JUKEBOX = register(
			"portable_jukebox", PortableJukeboxItem::new,
			new Item.Properties().stacksTo(1).useBlockDescriptionPrefix()
	);

	public static void init() {
		Components.init();
	}

	private static <T extends Item> T register(String id, Function<Item.Properties, T> item, Item.Properties settings) {
		var key = ResourceKey.create(Registries.ITEM, PortableJukebox.getID(id));
		return Registry.register(BuiltInRegistries.ITEM, key, item.apply(settings.setId(key)));
	}

}
