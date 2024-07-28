package se.datasektionen.mc.metacraft_core.gui;

import com.mojang.authlib.GameProfile;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.elements.GuiElementBuilderInterface;
import eu.pb4.sgui.api.gui.layered.LayeredGui;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class MultiplePlayerSelector extends LayeredGui {

	private final PagedLayer selectedSide;
	private final PagedLayer nonSelectedSide;

	private final SearchButton button;

	private final Set<GameProfile> nonSelectedPlayersSorted = new TreeSet<>(Comparator.comparing(GameProfile::getName));
	private final Set<GameProfile> selectedPlayersSorted = new TreeSet<>(Comparator.comparing(GameProfile::getName));

	private final Consumer<GameProfile> onSelected;
	private final Consumer<GameProfile> onDeselected;

	public MultiplePlayerSelector(
			ServerPlayerEntity player, Collection<GameProfile> selectedPlayers,
			Predicate<ServerPlayerEntity> isValid,
			Consumer<GameProfile> onSelected, Consumer<GameProfile> onDeselected,
			boolean addSearch, boolean allowSelfSelect
	) {
		super(ScreenHandlerType.GENERIC_9X4, player, true);

		if (!allowSelfSelect) {
			isValid = isValid.and(p -> p != player);
		}

		this.onSelected = onSelected;
		this.onDeselected = onDeselected;

		setTitle(Text.translatableWithFallback("gui.metacraft.player_selector", "Player Selector"));

		player.getServer().getPlayerManager().getPlayerList().stream().filter(isValid).map(
				ServerPlayerEntity::getGameProfile
		).filter(
				p -> !selectedPlayers.contains(p)
		).forEach(nonSelectedPlayersSorted::add);

		selectedPlayersSorted.addAll(selectedPlayers);

		setupBackground();
		if (addSearch) {
			setSlot(4, button = new SearchButton(Items.COMPASS, query -> updateLayers()));
		} else {
			button = null;
		}

		selectedSide = new PagedLayer(
				4, 4,
				GuiElementBuilder.from(new ItemStack(Items.RED_WOOL)),
				GuiElementBuilder.from(new ItemStack(Items.GREEN_WOOL)),
				GuiElementBuilder.from(new ItemStack(Items.AIR)).build()
		);

		nonSelectedSide = new PagedLayer(
				4, 4,
				GuiElementBuilder.from(new ItemStack(Items.RED_WOOL)),
				GuiElementBuilder.from(new ItemStack(Items.GREEN_WOOL)),
				GuiElementBuilder.from(new ItemStack(Items.AIR)).build()
		);
		addLayer(selectedSide, 0, 0);
		addLayer(nonSelectedSide, 5, 0);
		updateLayers();
	}

	private GuiElementBuilderInterface<?> makeButton(GameProfile profile) {
		return GuiElementBuilder.from(new ItemStack(Items.PLAYER_HEAD)).setSkullOwner(profile, getPlayer().getServer());
	}

	private boolean matchesSearchTerm(GameProfile profile) {
		if (button == null) return true;
		if (button.getSearchQuery().isBlank()) return true;
		return profile.getName().toLowerCase(Locale.ROOT).contains(
				button.getSearchQuery().toLowerCase(Locale.ROOT)
		);
	}

	private void updateLayers() {
		selectedSide.setElements(
				selectedPlayersSorted.stream().filter(this::matchesSearchTerm).map(
						profile -> makeButton(profile).setCallback(() -> deselectPlayer(profile)).build()
				).toList()
		);
		nonSelectedSide.setElements(
				nonSelectedPlayersSorted.stream().filter(this::matchesSearchTerm).map(
						profile -> makeButton(profile).setCallback(() -> selectPlayer(profile)).build()
				).toList()
		);
	}

	protected void selectPlayer(GameProfile profile) {
		nonSelectedPlayersSorted.remove(profile);
		selectedPlayersSorted.add(profile);
		updateLayers();
		onSelected.accept(profile);
	}

	protected void deselectPlayer(GameProfile profile) {
		selectedPlayersSorted.remove(profile);
		nonSelectedPlayersSorted.add(profile);
		updateLayers();
		onDeselected.accept(profile);
	}

	private void setupBackground() {
		for (int i = 0; i < getSize(); i++) {
			setSlot(i, new ItemStack(Items.ORANGE_STAINED_GLASS_PANE));
		}
	}


}
