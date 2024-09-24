package se.datasektionen.mc.metacraft_plots.item;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Rarity;
import net.minecraft.util.Unit;
import se.datasektionen.mc.metacraft_core.item.components.METAcraftComponents;
import se.datasektionen.mc.metacraft_plots.METAcraftPlots;

import java.util.Optional;

public class PlotItems {

	public static final Item PLOT_KEY = register(
			"plot_key",
			new PlotKey(
					new Item.Settings().rarity(Rarity.RARE),
					(stack, s) -> Optional.ofNullable(s).map(server -> {
						return PlotKey.getZone(stack, server).isPresent();
					}).orElse(PlotKey.getPlot(stack) != null) ? 255 : 254
			)
	);
	public static final Item PLOT_MASTER_KEY = register(
			"plot_master_key", new PlotKey(new Item.Settings().fireproof().component(
					METAcraftComponents.SOULBOUND, Unit.INSTANCE
			).rarity(Rarity.EPIC), (stack, server) -> 253)
	);

	public static void init() {
		PlotComponents.init();
	}

	private static <T extends Item> T register(String id, T item) {
		return Registry.register(Registries.ITEM, METAcraftPlots.getID(id), item);
	}

}
