package se.datasektionen.mc.metacraft_plots.item;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Rarity;
import net.minecraft.util.Unit;
import se.datasektionen.mc.metacraft_core.item.components.METAcraftComponents;
import se.datasektionen.mc.metacraft_lib.METAcraftLib;
import se.datasektionen.mc.metacraft_plots.METAcraftPlots;

import java.util.Optional;
import java.util.function.Function;

public class PlotItems {

	public static final Item PLOT_KEY = register(
			"plot_key",
			settings -> new PlotKey(
					settings,
					(stack, s) -> Optional.ofNullable(s).map(server -> {
						return PlotKey.getZone(stack, server).isPresent();
					}).orElse(PlotKey.getPlot(stack) != null) ?
							METAcraftLib.getID("key_yellow") :
							METAcraftLib.getID("key_red")
			),
			new Item.Settings().rarity(Rarity.RARE)
	);
	public static final Item PLOT_MASTER_KEY = register(
			"plot_master_key",
			settings -> new PlotKey(
					settings, (stack, server) -> METAcraftLib.getID("key_blue")
			), new Item.Settings().fireproof().component(
					METAcraftComponents.SOULBOUND, Unit.INSTANCE
			).rarity(Rarity.EPIC)
	);

	public static void init() {
		PlotComponents.init();
	}

	private static <T extends Item> T register(
			String id, Function<Item.Settings, T> item, Item.Settings settings
	) {
		var key = RegistryKey.of(RegistryKeys.ITEM, METAcraftPlots.getID(id));
		return Registry.register(Registries.ITEM, key, item.apply(settings.registryKey(key)));
	}

}
