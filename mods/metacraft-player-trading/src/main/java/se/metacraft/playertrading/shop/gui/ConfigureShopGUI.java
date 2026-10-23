package se.metacraft.playertrading.shop.gui;

import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.elements.GuiElement;
import eu.pb4.sgui.api.gui.SlotBasedGui;
import eu.pb4.sgui.api.gui.layered.Layer;
import eu.pb4.sgui.api.gui.layered.LayeredGui;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import se.metacraft.lib.util.helper.PCollectionsHelper;
import se.metacraft.playertrading.PlayerTrading;
import se.metacraft.playertrading.block.entities.ShopBlockEntity;
import se.metacraft.playertrading.shop.Shop;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ConfigureShopGUI extends LayeredGui implements ShopBlockEntity.UpdatableGUI {

	public static final FontDescription MENU_FONT = new FontDescription.Resource(PlayerTrading.getID("shop_config_menu"));

	private final List<OfferLayer> offers = new ArrayList<>();
	private final ShopBlockEntity shop;

	public ConfigureShopGUI(ServerPlayer player, ShopBlockEntity shop) {
		super(MenuType.GENERIC_9x3, player, false);

		// Font magic
		// a = move cursor by -8
		//     (back by 8, aligns to vanilla GUI corner)
		// b = shop_config_menu.png
		//     (width: 176, so moves cursor by 176)
		// c = move cursor by -169
		//     (back by 169 = -8 + 176 + 1, resets cursor.
		//      + 1 is to count space between characters)
		//e

		var fontMagic = Component.literal("abc").withStyle(style ->
			style
				.withFont(MENU_FONT)
				.withColor(ChatFormatting.WHITE)
		);

		setTitle(Component.literal("").append(fontMagic).append(Component.translatable("gui.metacraft.shop_config_menu")));

		for (int i = 0; i < 9; i++) {
			int y = i % 3;
			int x = (i / 3) * 3;
			var layer = new OfferLayer();
			offers.add(layer);
			addLayer(layer, x, y);
		}
		this.shop = shop;
		update();
	}

	@Override
	public void onRemoved() {
		shop.modifyShop(
			shop -> shop.setOffers(
				PCollectionsHelper.collect(
					offers.stream().map(
						OfferLayer::getOffer
					).filter(Optional::isPresent).map(Optional::get)
				)
			)
		);
		shop.onClosed(this);
	}

	@Override
	public void update() {
		shop.getShop().ifPresent(s -> {
			for (int i = 0; i < offers.size(); i++) {
				if (s.offers().size() > i) {
					offers.get(i).setOffer(s.offers().get(i));
				} else {
					offers.get(i).clearOffer();
				}
			}
		});
	}

	@Override
	public void close() {
		super.close();
	}

	public static class OfferLayer extends Layer {

		private final ItemConfigSlot buyA = new ItemConfigSlot();
		private final ItemConfigSlot buyB = new ItemConfigSlot();
		private final ItemConfigSlot result = new ItemConfigSlot();

		public OfferLayer() {
			super(1, 3);
			setSlot(0, buyA);
			setSlot(1, buyB);
			setSlot(2, result);
		}

		public void setOffer(Shop.SimpleOffer offer) {
			buyA.setCurrent(offer.price().left().create());
			buyB.setCurrent(offer.price().right().map(ItemStackTemplate::create).orElse(ItemStack.EMPTY));
			result.setCurrent(offer.result().create());
		}

		public void clearOffer() {
			buyA.setCurrent(ItemStack.EMPTY);
			buyB.setCurrent(ItemStack.EMPTY);
			result.setCurrent(ItemStack.EMPTY);
		}

		public Optional<Shop.SimpleOffer> getOffer() {
			if (buyA.getItemStack().isEmpty() || result.getItemStack().isEmpty()) {
				return Optional.empty();
			}
			return Optional.of(new Shop.SimpleOffer(
				new Shop.SimpleOffer.Price(
					ItemStackTemplate.fromNonEmptyStack(buyA.getItemStack()),
					buyB.getItemStack().isEmpty() ? Optional.empty() : Optional.of(ItemStackTemplate.fromNonEmptyStack(buyB.getItemStack()))
				),
				ItemStackTemplate.fromNonEmptyStack(result.getItemStack())
			));
		}

	}

	public static class ItemConfigSlot implements GuiElement, GuiElement.ClickCallback {

		private ItemStack current = ItemStack.EMPTY;

		public void setCurrent(ItemStack item) {
			current = item;
		}

		@Override
		public ItemStack getItemStack() {
			return current;
		}

		@Override
		public ClickCallback getGuiCallback() {
			return this;
		}

		private static void growToMaxSize(ItemStack item, int amount) {
			item.grow(Math.min(amount, item.getMaxStackSize() - item.count()));
		}

		private static void shrinkToZero(ItemStack item, int amount) {
			item.shrink(Math.max(amount, item.count() - item.getMaxStackSize()));
		}

		private boolean shouldSetFromCarried(SlotBasedGui gui) {
			var carried = gui.getPlayer().containerMenu.getCarried();
			if (carried.isEmpty()) return false;
			return !ItemStack.isSameItemSameComponents(current, carried);
		}

		private static boolean shouldReduce(ClickType type) {
			return type.isRight || type == ClickType.DROP || type == ClickType.CTRL_DROP;
		}

		@Override
		public void click(int index, ClickType type, ContainerInput action, SlotBasedGui gui) {
			var carried = gui.getPlayer().containerMenu.getCarried();

			if (shouldSetFromCarried(gui)) {
				if (type.isLeft) {
					current = carried.copy();
				} else {
					current = carried.copyWithCount(1);
				}
			} else if (type.shift || type == ClickType.CTRL_DROP) {
				int leftClickCount = carried.isEmpty() ? current.getMaxStackSize() : carried.count();
				if (shouldReduce(type)) {
					shrinkToZero(current, leftClickCount);
				} else {
					growToMaxSize(current, leftClickCount);
				}
			} else {
				if (shouldReduce(type)) {
					shrinkToZero(current, 1);
				} else {
					growToMaxSize(current, 1);
				}
			}
			if (current.isEmpty()) {
				current = ItemStack.EMPTY;
			}
		}
	}
}
