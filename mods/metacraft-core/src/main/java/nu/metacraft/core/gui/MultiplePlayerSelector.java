package nu.metacraft.core.gui;

import com.mojang.authlib.GameProfile;
import eu.pb4.sgui.api.SguiUtils;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.elements.GuiElement;
import eu.pb4.sgui.api.gui.layered.LayeredGui;
import it.unimi.dsi.fastutil.objects.ReferenceSortedSets;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.TooltipDisplay;
import nu.metacraft.lib.util.helper.GameProfileHelper;
import nu.metacraft.lib.util.helper.PlayerHelper;

import java.util.*;
import java.util.function.Consumer;

public abstract class MultiplePlayerSelector extends LayeredGui {

	private final PagedLayer selectedSide;
	private final PagedLayer nonSelectedSide;

	private SearchButton button;

	protected GuiElement background = GuiElementBuilder.from(
			new ItemStack(
					Items.ORANGE_STAINED_GLASS_PANE.builtInRegistryHolder(), 1,
					DataComponentPatch.builder().set(
							DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(
									true, ReferenceSortedSets.emptySet()
							)
					).build()
			)
	).build();

	protected int searchButtonIndex = -1;
	private int prevSearchButtonIndex = -1;

	private final Set<GameProfile> nonSelectedPlayersSorted;
	private final Set<GameProfile> selectedPlayersSorted;

	public static Comparator<GameProfile> getDefaultComparator(MinecraftServer server) {
		return Comparator.<GameProfile, String>comparing(
				profile -> GameProfileHelper.getNameFromProfile(profile, server).toLowerCase(Locale.ROOT)
		).thenComparing(
				GameProfile::id
		);
	}

	public MultiplePlayerSelector(
			MenuType<?> type,
			ServerPlayer player, Collection<GameProfile> selectedPlayers
	) {
		super(type, player, true);
		this.nonSelectedPlayersSorted = new TreeSet<>(getDefaultComparator(player.level().getServer()));
		this.selectedPlayersSorted = new TreeSet<>(getDefaultComparator(player.level().getServer()));
		setTitle(Component.translatableWithFallback("gui.metacraft.player_selector", "Player Selector"));

		selectedPlayersSorted.addAll(selectedPlayers);

		setupBackground();

		int width = SguiUtils.getWidth(getType())/2;

		selectedSide = new PagedLayer(
				playerViewHeight(), width,
				GuiElementBuilder.from(new ItemStack(Items.RED_STAINED_GLASS_PANE)).setName(PagedLayer.PREV_PAGE),
				GuiElementBuilder.from(new ItemStack(Items.GREEN_STAINED_GLASS_PANE)).setName(PagedLayer.NEXT_PAGE),
				GuiElementBuilder.from(new ItemStack(Items.AIR)).build()
		);

		nonSelectedSide = new PagedLayer(
				playerViewHeight(), width,
				GuiElementBuilder.from(new ItemStack(Items.RED_STAINED_GLASS_PANE)).setName(PagedLayer.PREV_PAGE),
				GuiElementBuilder.from(new ItemStack(Items.GREEN_STAINED_GLASS_PANE)).setName(PagedLayer.NEXT_PAGE),
				GuiElementBuilder.from(new ItemStack(Items.AIR)).build()
		);
		addLayer(selectedSide, width + 1, playerViewHeightOffset());
		addLayer(nonSelectedSide, 0, playerViewHeightOffset());
		updateLayers();
	}

	protected Comparator<GameProfile> customSelectedComparator() {
		return null;
	}

	protected Comparator<GameProfile> customNonSelectedComparator() {
		return null;
	}

	private boolean isReallyValid(ServerPlayer player) {
		return (allowSelfSelect() || player != getPlayer()) && isValid(player) && PlayerHelper.shouldShowInGUI(player);
	}

	protected abstract boolean isValid(ServerPlayer player);

	protected boolean allowSelfSelect() {
		return false;
	}

	protected int playerViewHeight() {
		return Math.max(SguiUtils.getHeight(getType())-1, 1);
	}

	protected int playerViewHeightOffset() {
		return 1;
	}

	protected abstract void onSelected(GameProfile player);

	protected abstract void onDeselected(GameProfile player);

	private GuiElement makeButton(GameProfile profile, GuiElement.ClickCallback callback) {
		return new DeferredPlayerHead(
				profile, DataComponentPatch.builder().set(
						DataComponents.ITEM_NAME, Component.literal(GameProfileHelper.getNameFromProfile(profile, getPlayer().level().getServer()))
				).build(), callback
		);
	}

	protected void updateSearchButtonPosition() {
		prevSearchButtonIndex = searchButtonIndex;
		if (selectedPlayersSorted.size() + nonSelectedPlayersSorted.size() > selectedSide.getMaxElementsPerPage() + nonSelectedSide.getMaxElementsPerPage()) {
			int width = SguiUtils.getWidth(getType());
			searchButtonIndex = width/2;
		} else {
			searchButtonIndex = -1;
		}
	}

	private boolean matchesSearchTerm(GameProfile profile) {
		if (button == null) return true;
		if (button.getSearchQuery().isBlank()) return true;
		return GameProfileHelper.getNameFromProfile(profile, getPlayer().level().getServer()).toLowerCase(Locale.ROOT).contains(
				button.getSearchQuery().toLowerCase(Locale.ROOT)
		);
	}

	private List<GuiElement> createButtons(
			Set<GameProfile> profiles, Comparator<GameProfile> customComparator,
			Consumer<GameProfile> onClick
	) {
		var stream = profiles.stream().filter(this::matchesSearchTerm);
		if (customComparator != null) {
			stream = stream.sorted(customComparator);
		}
		return stream.map(
				profile -> makeButton(profile, (a, b, c, d) -> onClick.accept(profile))
		).toList();
	}

	private void updateLayers() {
		getPlayer().level().getServer().getPlayerList().getPlayers().stream().filter(this::isReallyValid).map(
				ServerPlayer::getGameProfile
		).filter(
				p -> !selectedPlayersSorted.contains(p)
		).forEach(nonSelectedPlayersSorted::add);
		updateSearchButtonPosition();
		if (searchButtonIndex != prevSearchButtonIndex) {
			if (searchButtonIndex > 0) {
				setSlot(searchButtonIndex, button = new SearchButton(Items.SPYGLASS, query -> updateLayers()));
			} else {
				button = null;
			}
			if (prevSearchButtonIndex > 0) {
				setSlot(prevSearchButtonIndex, background);
			}
		}
		selectedSide.setElements(
				createButtons(selectedPlayersSorted, customSelectedComparator(), this::deselectPlayer)
		);
		nonSelectedSide.setElements(
				createButtons(nonSelectedPlayersSorted, customNonSelectedComparator(), this::selectPlayer)
		);
	}

	protected void selectPlayer(GameProfile profile) {
		nonSelectedPlayersSorted.remove(profile);
		selectedPlayersSorted.add(profile);
		updateLayers();
		onSelected(profile);
	}

	protected void deselectPlayer(GameProfile profile) {
		selectedPlayersSorted.remove(profile);
		nonSelectedPlayersSorted.add(profile);
		updateLayers();
		onDeselected(profile);
	}

	private void setupBackground() {
		for (int i = 0; i < getSize(); i++) {
			setSlot(i, background);
		}
	}


}
