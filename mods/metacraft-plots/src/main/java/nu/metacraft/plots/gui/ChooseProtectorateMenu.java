package nu.metacraft.plots.gui;

import com.mojang.authlib.GameProfile;
import eu.pb4.sgui.api.elements.AnimatedGuiElementBuilder;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.layered.Layer;
import eu.pb4.sgui.api.gui.layered.LayeredGui;
import nu.metacraft.core.util.helper.SGUIHelper;
import nu.metacraft.plots.zone.PlayerOwnedProtectorate;
import nu.metacraft.plots.zone.PlotDataTypes;
import nu.metacraft.zones.ZoneManager;

import java.util.*;
import java.util.function.Predicate;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class ChooseProtectorateMenu extends LayeredGui {


	private final List<PlayerOwnedProtectorate> primary = new ArrayList<>();
	private final List<PlayerOwnedProtectorate> membership = new ArrayList<>();
	private final List<PlayerOwnedProtectorate> others = new ArrayList<>();

	private List<PlayerOwnedProtectorate> currentMenu = null;
	private int currentPage = 0;

	Layer pageLayer = new Layer(3, 9);

	private final Predicate<PlayerOwnedProtectorate> onClick;

	public ChooseProtectorateMenu(ServerPlayer player, Predicate<PlayerOwnedProtectorate> onClick) {
		super(MenuType.GENERIC_9x4, player, true);
		this.setTitle(Component.translatableWithFallback(
				"protectorate.metacraft.gui.choose", "Choose Protectorate"
		));

		for (var zone : ZoneManager.getInstance(player.level().getServer()).getZones().getZones()) {
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
				Component.translatableWithFallback(
						"protectorate.metacraft.gui.choose.admin_page", "Admin Page"
				)
		).setCallback(
				(index, type, action) -> setPageLayer(primary)
		));
		controller.setSlot(1, GuiElementBuilder.from(new ItemStack(Items.ACACIA_HANGING_SIGN)).setName(
				Component.translatableWithFallback(
						"protectorate.metacraft.gui.choose.member_page", "Member Page"
				)
		).setCallback(
				(index, type, action) -> setPageLayer(membership)
		));
		controller.setSlot(2, GuiElementBuilder.from(new ItemStack(Items.GRASS_BLOCK)).setName(
				Component.translatableWithFallback(
						"protectorate.metacraft.gui.choose.other_page", "Other Page"
				)
		).setCallback(
				(index, type, action) -> setPageLayer(others)
		));

		controller.setSlot(5, GuiElementBuilder.from(new ItemStack(Items.BRICK)).setName(
				Component.translatableWithFallback(
						"protectorate.metacraft.gui.choose.prev_page", "Previous Page"
				)
		).setCallback(
				(index, type, action) -> setPageLayer(currentPage-1)
		));

		controller.setSlot(7, GuiElementBuilder.from(new ItemStack(Items.NETHER_BRICK)).setName(
				Component.translatableWithFallback(
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
				Component.literal(protectorate.getZone().getName()), getPlayer().level().getServer(),
				protectorate.getOwners().stream().map(
						owner -> getPlayer().level().getServer().services().nameToIdCache().get(owner).orElse(null)
				).filter(Objects::nonNull).map(config -> new GameProfile(config.id(), config.name()))
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
