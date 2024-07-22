package se.datasektionen.mc.metacraft_plots.gui;

import eu.pb4.sgui.api.elements.AnimatedGuiElementBuilder;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.layered.Layer;
import eu.pb4.sgui.api.gui.layered.LayeredGui;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import se.datasektionen.mc.metacraft_core.util.helper.SGUIHelper;
import se.datasektionen.mc.metacraft_plots.zone.PlayerOwnedProtectorate;
import se.datasektionen.mc.metacraft_plots.zone.PlotDataTypes;
import se.datasektionen.mc.zones.ZoneManager;

import java.util.*;
import java.util.function.Predicate;

public class ChooseProtectorateMenu extends LayeredGui {


	private final List<PlayerOwnedProtectorate> primary = new ArrayList<>();
	private final List<PlayerOwnedProtectorate> membership = new ArrayList<>();
	private final List<PlayerOwnedProtectorate> others = new ArrayList<>();

	private List<PlayerOwnedProtectorate> currentMenu = null;
	private int currentPage = 0;

	Layer pageLayer = new Layer(3, 9);

	private final Predicate<PlayerOwnedProtectorate> onClick;

	public ChooseProtectorateMenu(ServerPlayerEntity player, Predicate<PlayerOwnedProtectorate> onClick) {
		super(ScreenHandlerType.GENERIC_9X4, player, true);
		this.setTitle(Text.translatableWithFallback(
				"protectorate.metacraft.gui.choose", "Choose Protectorate"
		));

		for (var zone : ZoneManager.getInstance(player.server).getZones().getZones()) {
			zone.get(PlotDataTypes.PLAYER_PROTECTORATE).ifPresent(protectorate -> {
				if (protectorate.isAdmin(player)) {
					primary.add(protectorate);
					return;
				}
				if (protectorate.isAllowed(player)) {
					membership.add(protectorate);
					return;
				}
				others.add(protectorate);
			});
		}
		primary.sort(Comparator.comparing(element -> element.getZone().getName()));
		membership.sort(Comparator.comparing(element -> element.getZone().getName()));
		others.sort(Comparator.comparing(element -> element.getZone().getName()));

		if (!primary.isEmpty()) {
			setPageLayer(primary);
		} else if (!membership.isEmpty()) {
			setPageLayer(membership);
		} else {
			setPageLayer(others);
		}

		this.addLayer(pageLayer, 0, 0);

		Layer controller = new Layer(1, 9);
		controller.setSlot(0, GuiElementBuilder.from(new ItemStack(Items.END_CRYSTAL)).setName(
				Text.translatableWithFallback(
						"protectorate.metacraft.gui.choose.admin_page", "Admin Page"
				)
		).setCallback(
				(index, type, action) -> setPageLayer(primary)
		));
		controller.setSlot(1, GuiElementBuilder.from(new ItemStack(Items.ACACIA_HANGING_SIGN)).setName(
				Text.translatableWithFallback(
						"protectorate.metacraft.gui.choose.member_page", "Member Page"
				)
		).setCallback(
				(index, type, action) -> setPageLayer(membership)
		));
		controller.setSlot(2, GuiElementBuilder.from(new ItemStack(Items.GRASS_BLOCK)).setName(
				Text.translatableWithFallback(
						"protectorate.metacraft.gui.choose.other_page", "Other Page"
				)
		).setCallback(
				(index, type, action) -> setPageLayer(others)
		));

		controller.setSlot(5, GuiElementBuilder.from(new ItemStack(Items.BRICK)).setName(
				Text.translatableWithFallback(
						"protectorate.metacraft.gui.choose.prev_page", "Previous Page"
				)
		).setCallback(
				(index, type, action) -> setPageLayer(currentPage-1)
		));

		controller.setSlot(7, GuiElementBuilder.from(new ItemStack(Items.NETHER_BRICK)).setName(
				Text.translatableWithFallback(
						"protectorate.metacraft.gui.choose.next_page", "Next Page"
				)
		).setCallback(
				(index, type, action) -> setPageLayer(currentPage+1)
		));

		this.addLayer(controller, 0, 3);

		this.onClick = onClick;
	}

	public AnimatedGuiElementBuilder createIconFrom(PlayerOwnedProtectorate protectorate) {
		return SGUIHelper.createGameProfileHeadIcon(
				Text.literal(protectorate.getZone().getName()), getPlayer().getServer(),
				protectorate.getOwners().stream().map(
						owner -> getPlayer().server.getUserCache().getByUuid(owner).orElse(null)
				).filter(Objects::nonNull)
		);
	}

	public void setPageLayer(int page) {
		setPageLayerFromProtectorates(currentMenu, page);
	}

	public void setPageLayer(List<PlayerOwnedProtectorate> protectorates) {
		setPageLayerFromProtectorates(protectorates, currentPage);
	}

	public void setPageLayerFromProtectorates(List<PlayerOwnedProtectorate> protectorates, int page) {
		pageLayer.clearSlots();
		if (page < 0) {
			page = 0;
		}
		currentMenu = Collections.unmodifiableList(protectorates);
		final int perRow = 4;
		final int cols = 3;
		final int numPerPage = perRow * cols;

		int pageStart = page * numPerPage;
		if (protectorates.size() < pageStart) {
			page = protectorates.size() / numPerPage;
			pageStart = page * numPerPage;
		}
		currentPage = page;
		if (pageStart < 0) {
			pageStart = 0;
		}
		int pageEnd = pageStart + numPerPage;

		for (int i = pageStart; i < pageEnd && i < protectorates.size(); i++) {
			int rowIndex = ((i - pageStart) % perRow) * 2 + 1;
			int col = (i - pageStart) / perRow;
			var protectorate = protectorates.get(i);
			pageLayer.setSlot(rowIndex + col * pageLayer.getWidth(), createIconFrom(protectorate).setCallback(
					(index, type, action) -> {
						if (onClick.test(protectorate)) {
							close();
						}
					}
			));
		}

	}
}
