package nu.metacraft.core.item;

import eu.pb4.polymer.core.api.item.PolymerBlockItem;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import nu.metacraft.core.METAcraftCore;
import nu.metacraft.core.block.METAcraftBlocks;
import nu.metacraft.core.item.items.Wrench;

import java.util.function.Function;

public class METAcraftItems {

	public static final Item PORTAL_PADDING = register(
			"portal_padding", settings -> new PolymerBlockItem(METAcraftBlocks.PORTAL_PADDING, settings, Items.ENDER_PEARL),
			new Item.Settings().useBlockPrefixedTranslationKey()
	);

	public static final Item WRENCH = register(
			"wrench", Wrench::new,
			new Item.Settings().maxCount(1)
	);

	public static void init() {

	}

	private static Item register(String id, Function<Item.Settings, Item> creator, Item.Settings settings) {
		var key = RegistryKey.of(RegistryKeys.ITEM, METAcraftCore.getID(id));
		var item = Registry.register(Registries.ITEM, key, creator.apply(settings.registryKey(key)));
		if (item instanceof BlockItem b) {
			Item.BLOCK_ITEMS.put(b.getBlock(), b);
		}
		return item;
	}

}
