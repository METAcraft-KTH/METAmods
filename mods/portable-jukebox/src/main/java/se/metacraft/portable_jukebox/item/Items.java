package se.metacraft.portable_jukebox.item;

import se.metacraft.lib.CreativeTabNamespaces;
import se.metacraft.portable_jukebox.item.components.Components;
import se.metacraft.portable_jukebox.PortableJukebox;

import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import se.metacraft.portable_jukebox.item.components.PortableJukeboxInvData;

public class Items {

	public static final Item PORTABLE_JUKEBOX = register(
			"portable_jukebox", se.metacraft.portable_jukebox.item.PortableJukeboxItem::new,
			new Item.Properties().stacksTo(1).useBlockDescriptionPrefix().component(
				Components.PORTABLE_JUKEBOX, PortableJukeboxInvData.EMPTY
			)
	);

	public static final JukeboxUpgradeItem JUKEBOX_UPGRADE_TIER_1 = registerUpgrade(1, Rarity.RARE);
	public static final JukeboxUpgradeItem JUKEBOX_UPGRADE_TIER_2 = registerUpgrade(2, Rarity.EPIC);

	public static void init() {
		Components.init();
		CreativeTabNamespaces.add(PortableJukebox.NAMESPACE);
	}

	private static JukeboxUpgradeItem registerUpgrade(int tier, Rarity rarity) {
		return register(
			"jukebox_upgrade_tier_" + tier, settings -> new JukeboxUpgradeItem(settings, tier),
			new Item.Properties().stacksTo(16).rarity(rarity).component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
		);
	}

	private static <T extends Item> T register(String id, Function<Item.Properties, T> item, Item.Properties settings) {
		var key = ResourceKey.create(Registries.ITEM, PortableJukebox.getID(id));
		return Registry.register(BuiltInRegistries.ITEM, key, item.apply(settings.setId(key)));
	}

}
