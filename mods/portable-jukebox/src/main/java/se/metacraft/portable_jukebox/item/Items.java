package se.metacraft.portable_jukebox.item;

import net.minecraft.util.Unit;
import se.metacraft.lib.CreativeTabNamespaces;
import se.metacraft.portable_jukebox.BoomboxConfig;
import se.metacraft.portable_jukebox.item.components.Components;
import se.metacraft.portable_jukebox.Boombox;

import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import se.metacraft.portable_jukebox.item.components.BoomboxInvData;

public class Items {

	public static final Item BOOMBOX = register(
			"portable_jukebox", BoomboxItem::new,
			applyComponentsFromConfig(
				new Item.Properties().stacksTo(1).useBlockDescriptionPrefix().component(
					Components.BOOMBOX, BoomboxInvData.EMPTY
				)
			)
	);

	private static Item.Properties applyComponentsFromConfig(Item.Properties properties) {
		var config = BoomboxConfig.getConfig().defaultUpgrades();
		if (config.speed()) {
			properties.component(Components.SPEED_BUTTONS, Unit.INSTANCE);
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
		CreativeTabNamespaces.add(Boombox.NAMESPACE);
	}

	private static <T extends Item> T register(String id, Function<Item.Properties, T> item, Item.Properties settings) {
		var key = ResourceKey.create(Registries.ITEM, Boombox.getID(id));
		return Registry.register(BuiltInRegistries.ITEM, key, item.apply(settings.setId(key)));
	}

}
