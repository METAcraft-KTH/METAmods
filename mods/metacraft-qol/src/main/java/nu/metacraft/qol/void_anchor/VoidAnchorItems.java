package nu.metacraft.qol.void_anchor;

import nu.metacraft.lib.CreativeTabNamespaces;
import nu.metacraft.qol.Qol;
import eu.pb4.polymer.core.api.item.PolymerBlockItem;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.function.Function;

public class VoidAnchorItems {

	public static final Item VOID_ANCHOR = register(
			"void_anchor", settings -> new PolymerBlockItem(VoidAnchorBlocks.VOID_ANCHOR.value(), settings, Items.RESPAWN_ANCHOR, true),
			new Item.Properties().useBlockDescriptionPrefix()
	);

	public static void init() {
		CreativeTabNamespaces.add(Qol.NAMESPACE);
	}

	private static Item register(String id, Function<Item.Properties, Item> creator, Item.Properties settings) {
		var key = ResourceKey.create(Registries.ITEM, Qol.getID(id));
		var item = Registry.register(BuiltInRegistries.ITEM, key, creator.apply(settings.setId(key)));
		if (item instanceof BlockItem b) {
			Item.BY_BLOCK.put(b.getBlock(), b);
		}
		return item;
	}

}
