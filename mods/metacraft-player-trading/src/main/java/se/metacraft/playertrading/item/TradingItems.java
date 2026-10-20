package se.metacraft.playertrading.item;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import nu.metacraft.lib.CreativeTabNamespaces;
import se.metacraft.playertrading.PlayerTrading;
import se.metacraft.playertrading.block.TradingBlocks;
import se.metacraft.playertrading.component.TradingComponents;
import se.metacraft.playertrading.item.items.ShopItem;
import se.metacraft.playertrading.item.items.ShopKey;
import se.metacraft.playertrading.shop.Shop;
import se.metacraft.playertrading.shop.ShopType;

import java.util.Optional;
import java.util.function.Function;

public class TradingItems {

	public static final Item SHOP = register(
		"shop", properties -> new ShopItem(
			TradingBlocks.SHOP.value(), TradingBlocks.SHOP_WALL.value(), properties
		),
		new Item.Properties().component(
			TradingComponents.SHOP,
			Shop.create(new ShopType.Player(Optional.empty()))
		).useBlockDescriptionPrefix()
	);

	public static final Item ADMIN_SHOP = register(
		"admin_shop", properties -> new ShopItem(
			TradingBlocks.SHOP.value(), TradingBlocks.SHOP_WALL.value(), properties
		),
		new Item.Properties().component(
			TradingComponents.SHOP,
			Shop.create(ShopType.Admin.getInstance())
		).useBlockDescriptionPrefix()
	);

	public static final Item SINGLE_USE_SHOP = register(
		"single_use_shop", properties -> new ShopItem(
			TradingBlocks.SHOP.value(), TradingBlocks.SHOP_WALL.value(), properties
		),
		new Item.Properties().component(
			TradingComponents.SHOP,
			Shop.create(ShopType.LimitedUses.SINGLE_USE_SHOP)
		).useBlockDescriptionPrefix()
	);

	public static final Item SHOP_KEY = register("shop_key", ShopKey::new, new Item.Properties());

	public static void init() {
		CreativeTabNamespaces.add(PlayerTrading.NAMESPACE);
	}

	private static Item register(String id, Function<Item.Properties, Item> creator, Item.Properties settings) {
		var key = ResourceKey.create(Registries.ITEM, PlayerTrading.getID(id));
		var item = Registry.register(BuiltInRegistries.ITEM, key, creator.apply(settings.setId(key)));
		if (item instanceof BlockItem b) {
			Item.BY_BLOCK.put(b.getBlock(), b);
		}
		return item;
	}

}
