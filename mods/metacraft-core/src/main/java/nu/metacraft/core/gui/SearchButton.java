package nu.metacraft.core.gui;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.elements.GuiElementBuilderInterface;
import eu.pb4.sgui.api.elements.GuiElementInterface;
import eu.pb4.sgui.api.gui.AnvilInputGui;
import eu.pb4.sgui.api.gui.GuiInterface;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class SearchButton implements GuiElementInterface {

	private String searchQuery = "";

	private final Supplier<ItemStack> stackSupplier;
	private final BiFunction<ItemStack, GuiInterface, ItemStack> updater;
	private final BiFunction<ItemStack, String, ItemStack> searchQueryAppender;
	private final Consumer<String> onUpdate;

	public static final BiFunction<ItemStack, String, ItemStack> DEFAULT = (stack, query) -> {
		stack.set(DataComponentTypes.ITEM_NAME, Text.translatableWithFallback(
				"gui.metacraft.search", "Search: " + query, query
		));
		return stack;
	};

	public SearchButton(Item item, Consumer<String> onUpdate) {
		this(
				new Supplier<>() {

					private final ItemStack stack = new ItemStack(item);

					@Override
					public ItemStack get() {
						return stack;
					}
				},
				(stack, gui) -> stack, DEFAULT, onUpdate
		);
	}

	public SearchButton(
			Supplier<ItemStack> stackSupplier,
			BiFunction<ItemStack, GuiInterface, ItemStack> updater,
			BiFunction<ItemStack, String, ItemStack> searchQueryAppender,
			Consumer<String> onUpdate
	) {
		this.stackSupplier = stackSupplier;
		this.updater = updater;
		this.searchQueryAppender = searchQueryAppender;
		this.onUpdate = onUpdate;
	}

	private void update(String newQuery) {
		searchQuery = newQuery;
		onUpdate.accept(searchQuery);
	}

	public String getSearchQuery() {
		return searchQuery;
	}

	@Override
	public ItemStack getItemStack() {
		return stackSupplier.get();
	}

	@Override
	public ItemStack getItemStackForDisplay(GuiInterface gui) {
		return searchQueryAppender.apply(updater.apply(getItemStack(), gui), searchQuery).copy();
	}

	@Override
	public ClickCallback getGuiCallback() {
		return (index, type, action, gui) -> {
			var searchMenu = new SearchMenu(
					gui.getPlayer(), this::update,
					GuiElementBuilder.from(new ItemStack(Items.SPYGLASS)).setName(Text.translatableWithFallback(
							"gui.metacraft.search", "Search"
					)), gui
			);
			searchMenu.open();
		};
	}

	public static class SearchMenu extends AnvilInputGui {

		private final Consumer<String> acceptor;
		private final GuiInterface prev;

		private final GuiElementInterface closeButton;

		public SearchMenu(
				ServerPlayerEntity player, Consumer<String> acceptor,
				GuiElementBuilderInterface<?> closeButton,
				GuiInterface prev
		) {
			super(player, true);
			this.acceptor = acceptor;
			this.prev = prev;
			setSlot(2, this.closeButton = closeButton.setCallback(() -> close()).build());
		}

		@Override
		public void onInput(String input) {
			acceptor.accept(input);
			setSlot(2, this.closeButton);
		}

		@Override public void onClose() {
			prev.open();
		}
	}
}
