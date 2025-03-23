package se.datasektionen.mc.metacraft_core.gui;

import com.mojang.authlib.GameProfile;
import eu.pb4.sgui.api.GuiHelpers;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.elements.GuiElementInterface;
import eu.pb4.sgui.api.gui.layered.LayeredGui;
import it.unimi.dsi.fastutil.objects.ReferenceSortedSets;
import net.minecraft.component.ComponentChanges;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import se.datasektionen.mc.metacraft_lib.util.helper.GameProfileHelper;
import se.datasektionen.mc.metacraft_lib.util.helper.PlayerHelper;

import java.util.*;
import java.util.function.Consumer;

public abstract class MultiplePlayerSelector extends LayeredGui {

	private final PagedLayer selectedSide;
	private final PagedLayer nonSelectedSide;

	private SearchButton button;

	protected GuiElementInterface background = GuiElementBuilder.from(
			new ItemStack(
					Items.ORANGE_STAINED_GLASS_PANE.getRegistryEntry(), 1,
					ComponentChanges.builder().add(
							DataComponentTypes.TOOLTIP_DISPLAY, new TooltipDisplayComponent(
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
				GameProfile::getId
		);
	}

	public MultiplePlayerSelector(
			ScreenHandlerType<?> type,
			ServerPlayerEntity player, Collection<GameProfile> selectedPlayers
	) {
		super(type, player, true);
		this.nonSelectedPlayersSorted = new TreeSet<>(getDefaultComparator(player.getServer()));
		this.selectedPlayersSorted = new TreeSet<>(getDefaultComparator(player.getServer()));
		setTitle(Text.translatableWithFallback("gui.metacraft.player_selector", "Player Selector"));

		selectedPlayersSorted.addAll(selectedPlayers);

		setupBackground();

		int width = GuiHelpers.getWidth(getType())/2;

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

	private boolean isReallyValid(ServerPlayerEntity player) {
		return (allowSelfSelect() || player != getPlayer()) && isValid(player) && PlayerHelper.shouldShowInGUI(player);
	}

	protected abstract boolean isValid(ServerPlayerEntity player);

	protected boolean allowSelfSelect() {
		return false;
	}

	protected int playerViewHeight() {
		return Math.max(GuiHelpers.getHeight(getType())-1, 1);
	}

	protected int playerViewHeightOffset() {
		return 1;
	}

	protected abstract void onSelected(GameProfile player);

	protected abstract void onDeselected(GameProfile player);

	private GuiElementInterface makeButton(GameProfile profile, GuiElementInterface.ClickCallback callback) {
		return new DeferredPlayerHead(
				profile, ComponentChanges.builder().add(
						DataComponentTypes.ITEM_NAME, Text.literal(GameProfileHelper.getNameFromProfile(profile, getPlayer().getServer()))
				).build(), callback
		);
	}

	protected void updateSearchButtonPosition() {
		prevSearchButtonIndex = searchButtonIndex;
		if (selectedPlayersSorted.size() + nonSelectedPlayersSorted.size() > selectedSide.getMaxElementsPerPage() + nonSelectedSide.getMaxElementsPerPage()) {
			int width = GuiHelpers.getWidth(getType());
			searchButtonIndex = width/2;
		} else {
			searchButtonIndex = -1;
		}
	}

	private boolean matchesSearchTerm(GameProfile profile) {
		if (button == null) return true;
		if (button.getSearchQuery().isBlank()) return true;
		return GameProfileHelper.getNameFromProfile(profile, getPlayer().getServer()).toLowerCase(Locale.ROOT).contains(
				button.getSearchQuery().toLowerCase(Locale.ROOT)
		);
	}

	private List<GuiElementInterface> createButtons(
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
		getPlayer().getServer().getPlayerManager().getPlayerList().stream().filter(this::isReallyValid).map(
				ServerPlayerEntity::getGameProfile
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
