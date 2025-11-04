package nu.metacraft.relay.items;

import eu.pb4.polymer.core.api.item.PolymerBlockItem;
import nu.metacraft.relay.Relay;
import nu.metacraft.relay.blocks.RelayBlocks;
import org.pcollections.HashTreePMap;
import org.pcollections.HashTreePSet;

import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

public class RelayItems {

	public static final TagKey<Item> RELAY_RECHARGE_ITEMS = TagKey.create(
			Registries.ITEM, Relay.getID("relay_recharge_items")
	);

	public static final Item RELAY = register(
			"relay", settings -> new PolymerBlockItem(RelayBlocks.RELAY, settings, Items.STONE, true),
			new Item.Properties().useBlockDescriptionPrefix()
					.component(RelayComponents.VALID_DIMENSIONS, HashTreePMap.singleton(Level.END, HashTreePSet.singleton(Level.END)))
					.component(RelayComponents.VALID_CHARGE_ITEM, RELAY_RECHARGE_ITEMS)
					.component(RelayComponents.BLOCK_MODEL, Relay.getID("relay"))
	);

	public static void init() {

	}

	private static Item register(String id, Function<Item.Properties, Item> creator, Item.Properties settings) {
		var key = ResourceKey.create(Registries.ITEM, Relay.getID(id));
		var item = Registry.register(BuiltInRegistries.ITEM, key, creator.apply(settings.setId(key)));
		if (item instanceof BlockItem b) {
			Item.BY_BLOCK.put(b.getBlock(), b);
		}
		return item;
	}
}
