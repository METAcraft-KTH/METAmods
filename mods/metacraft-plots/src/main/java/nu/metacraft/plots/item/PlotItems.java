package nu.metacraft.plots.item;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Unit;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import nu.metacraft.core.item.components.METAcraftComponents;
import nu.metacraft.lib.METAcraftLib;
import nu.metacraft.plots.METAcraftPlots;

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
			new Item.Properties().rarity(Rarity.RARE)
	);
	public static final Item PLOT_MASTER_KEY = register(
			"plot_master_key",
			settings -> new PlotKey(
					settings, (stack, server) -> METAcraftLib.getID("key_blue")
			), new Item.Properties().fireResistant().component(
					METAcraftComponents.SOULBOUND, Unit.INSTANCE
			).rarity(Rarity.EPIC)
	);

	public static void init() {
		PlotComponents.init();
	}

	private static <T extends Item> T register(
			String id, Function<Item.Properties, T> item, Item.Properties settings
	) {
		var key = ResourceKey.create(Registries.ITEM, METAcraftPlots.getID(id));
		return Registry.register(BuiltInRegistries.ITEM, key, item.apply(settings.setId(key)));
	}

}
