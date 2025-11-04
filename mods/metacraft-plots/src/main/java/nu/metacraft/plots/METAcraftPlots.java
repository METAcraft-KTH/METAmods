package nu.metacraft.plots;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import nu.metacraft.plots.item.PlotItems;
import nu.metacraft.plots.zone.PlotDataTypes;

public class METAcraftPlots implements ModInitializer {
	public static final Logger LOGGER = LogManager.getLogger("METAcraft-Plots");

	public static final String NAMESPACE = "metacraft_plots";

	@Override
	public void onInitialize() {
		PlotItems.init();
		PlotDataTypes.init();
		Commands.registerCommands();
		Events.init();
	}

	public static ResourceLocation getID(String name) {
		return ResourceLocation.fromNamespaceAndPath(NAMESPACE, name);
	}
}
