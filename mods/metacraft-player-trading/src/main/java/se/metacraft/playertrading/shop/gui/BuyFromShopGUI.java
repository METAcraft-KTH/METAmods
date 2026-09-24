package se.metacraft.playertrading.shop.gui;

import eu.pb4.sgui.api.gui.MerchantGui;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import org.jspecify.annotations.NonNull;
import se.metacraft.playertrading.block.entities.ShopBlockEntity;
import se.metacraft.playertrading.criteria.ShopCriteriaTriggers;
import se.metacraft.playertrading.shop.Shop;
import se.metacraft.playertrading.shop.ShopType;

import java.util.List;
import java.util.stream.Stream;

public class BuyFromShopGUI extends MerchantGui implements ShopBlockEntity.UpdatableGUI {

	private final ShopBlockEntity shop;

	public BuyFromShopGUI(ServerPlayer player, ShopBlockEntity shop) {
		super(player, false);
		setTitle(Component.translatable("gui.metacraft.shop"));
		this.shop = shop;
	}

	@Override
	public void onRemoved() {
		shop.onClosed(this);
	}

	@Override
	public boolean open() {
		if (super.open()) {
			update();
			return true;
		}
		return false;
	}

	// This function runs 3 times every trade, likely a bug in Polymer.
	@Override
	public boolean onTrade(MerchantOffer offer) {
		return shop.getShop().map(shop -> shouldAcceptTrade(shop.shopType(), getOfferIndex(offer))).orElse(false);
	}

	public boolean shouldAcceptTrade(ShopType shopType, int slot) {
		return shop.getRemainingUses(slot) > 0 && shop.isTradeAllowed(player, slot);
	}

	public void onAcceptTrade(ShopType shopType, int slot, List<ItemStack> extractedItems) {
		switch (shopType.use(shop, slot)) {
			case ShopType.UseResult.RemoveTrade removed -> {
				shop.modifyShop(s -> s.withShopType(removed.type()).removeSlots(removed.removedSlots()));
				shop.getShop().ifPresent(shop -> {
					if (shop.offers().isEmpty()) {
						this.shop.destroy();
					}
				});
			}
			case ShopType.UseResult.Updated updated -> {
				shop.modifyShop(s -> s.withShopType(updated.type()));
			}
			default -> {}
		}
		ShopCriteriaTriggers.TRADE.trigger(player, this.shop, getCustomSlot(2).getItem());
		shopType.handleTakenItems(this.shop, extractedItems);
	}

	@Override
	public void update() {
		merchant.getOffers().clear();
		shop.getShop().ifPresent(shop -> {
			int slot = 0;
			for (var trade : shop.offers()) {
				addTrade(toOffer(trade, slot));
				slot++;
			}
		});
		merchantInventory.updateSellItem();
	}

	public MerchantOffer toOffer(Shop.SimpleOffer offer, int slot) {
		return new MerchantOffer(
			Shop.SimpleOffer.costOf(offer.price().left()), offer.price().right().map(Shop.SimpleOffer::costOf),
			offer.result().create(), shop.isTradeAllowed(player, slot) ? this.shop.getRemainingUses(slot) : 0, 0, 0
		) {
			@Override
			public boolean take(final @NonNull ItemStack buyA, final @NonNull ItemStack buyB) {
				var toStoreA = buyA.copyWithCount(offer.price().left().count());
				var toStoreB = offer.price().right().map(i -> buyB.copyWithCount(i.count())).orElse(ItemStack.EMPTY);
				if (super.take(buyA, buyB)) {
					shop.getShop().ifPresent(shop -> {
						onAcceptTrade(shop.shopType(), slot, Stream.of(toStoreA, toStoreB).filter(i -> !i.isEmpty()).toList());
					});
					return true;
				} else {
					return false;
				}

			}
		};
	}

	@Override
	public void close() {
		super.close();
	}
}
