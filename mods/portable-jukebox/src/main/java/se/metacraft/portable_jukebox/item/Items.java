package se.metacraft.portable_jukebox.item;

import net.minecraft.util.Unit;
import se.metacraft.lib.CreativeTabNamespaces;
import se.metacraft.portable_jukebox.PortableJukeboxConfig;
import se.metacraft.portable_jukebox.item.components.Components;
import se.metacraft.portable_jukebox.PortableJukebox;

import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import se.metacraft.portable_jukebox.item.components.PortableJukeboxInvData;

public class Items {

	public static final Item PORTABLE_JUKEBOX = register(
			"portable_jukebox", PortableJukeboxItem::new,
			applyComponentsFromConfig(
				new Item.Properties().stacksTo(1).useBlockDescriptionPrefix().component(
					Components.PORTABLE_JUKEBOX, PortableJukeboxInvData.EMPTY
				)
			)
	);

	private static Item.Properties applyComponentsFromConfig(Item.Properties properties) {
		var config = PortableJukeboxConfig.getConfig().defaultUpgrades();
		if (config.pitch()) {
			properties.component(Components.PITCH_BUTTONS, Unit.INSTANCE);
		}
		if (config.volume()) {
			properties.component(Components.VOLUME_BUTTONS, Unit.INSTANCE);
		}
		if (config.playlist()) {
			properties.component(Components.PLAYLIST_TOGGLE, Unit.INSTANCE);
		}
		if (config.shuffle()) {
			properties.component(Components.SHUFFLE_TOGGLE, Unit.INSTANCE);
		}
		int storage = config.storage();
		if (storage > 0) {
			properties.component(Components.STORAGE_SIZE, storage);
		}
		return properties;
	}

	public static void init() {
		Components.init();
		CreativeTabNamespaces.add(PortableJukebox.NAMESPACE);
	}

	private static <T extends Item> T register(String id, Function<Item.Properties, T> item, Item.Properties settings) {
		var key = ResourceKey.create(Registries.ITEM, PortableJukebox.getID(id));
		return Registry.register(BuiltInRegistries.ITEM, key, item.apply(settings.setId(key)));
	}

}
