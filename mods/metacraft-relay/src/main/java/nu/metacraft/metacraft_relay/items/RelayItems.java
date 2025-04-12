package nu.metacraft.metacraft_relay.items;

import eu.pb4.polymer.core.api.item.PolymerBlockItem;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.world.World;
import nu.metacraft.metacraft_relay.Relay;
import nu.metacraft.metacraft_relay.blocks.RelayBlocks;
import org.pcollections.HashTreePMap;
import org.pcollections.HashTreePSet;

import java.util.function.Function;

public class RelayItems {

	public static final TagKey<Item> RELAY_RECHARGE_ITEMS = TagKey.of(
			RegistryKeys.ITEM, Relay.getID("relay_recharge_items")
	);

	public static final Item RELAY = register(
			"relay", settings -> new PolymerBlockItem(RelayBlocks.RELAY, settings),
			new Item.Settings().component(RelayComponents.VALID_DIMENSIONS, HashTreePMap.singleton(World.END, HashTreePSet.singleton(World.END)))
					.component(RelayComponents.VALID_CHARGE_ITEM, RELAY_RECHARGE_ITEMS)
					.component(RelayComponents.BLOCK_MODEL, Relay.getID("relay"))
	);

	public static void init() {

	}

	private static Item register(String id, Function<Item.Settings, Item> creator, Item.Settings settings) {
		var key = RegistryKey.of(RegistryKeys.ITEM, Relay.getID(id));
		var item = Registry.register(Registries.ITEM, key, creator.apply(settings.registryKey(key)));
		if (item instanceof BlockItem b) {
			Item.BLOCK_ITEMS.put(b.getBlock(), b);
		}
		return item;
	}
}
