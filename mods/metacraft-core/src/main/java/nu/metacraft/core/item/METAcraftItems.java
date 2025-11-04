package nu.metacraft.core.item;

import eu.pb4.polymer.core.api.item.PolymerBlockItem;
import nu.metacraft.core.METAcraftCore;
import nu.metacraft.core.block.METAcraftBlocks;
import nu.metacraft.core.item.items.Wrench;

import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public class METAcraftItems {

	public static final Item PORTAL_PADDING = register(
			"portal_padding", settings -> new PolymerBlockItem(METAcraftBlocks.PORTAL_PADDING, settings, Items.ENDER_PEARL),
			new Item.Properties().useBlockDescriptionPrefix()
	);

	public static final Item WRENCH = register(
			"wrench", Wrench::new,
			new Item.Properties().stacksTo(1)
	);

	public static void init() {

	}

	private static Item register(String id, Function<Item.Properties, Item> creator, Item.Properties settings) {
		var key = ResourceKey.create(Registries.ITEM, METAcraftCore.getID(id));
		var item = Registry.register(BuiltInRegistries.ITEM, key, creator.apply(settings.setId(key)));
		if (item instanceof BlockItem b) {
			Item.BY_BLOCK.put(b.getBlock(), b);
		}
		return item;
	}

}
