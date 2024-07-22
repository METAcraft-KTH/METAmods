package se.datasektionen.mc.metacraft_plots.zone;

import net.minecraft.registry.Registry;
import se.datasektionen.mc.metacraft_plots.METAcraftPlots;
import se.datasektionen.mc.zones.zone.data.ZoneData;
import se.datasektionen.mc.zones.zone.data.ZoneDataRegistry;
import se.datasektionen.mc.zones.zone.data.ZoneDataType;

public class PlotDataTypes {

	public static final ZoneDataType<PlotData> PLOT = register(
			"plot", new ZoneDataType<>(PlotData.CODEC, PlotData::new)
	);

	public static final ZoneDataType<PlayerOwnedProtectorate> PLAYER_PROTECTORATE = register(
			"player_protectorate", new ZoneDataType<>(PlayerOwnedProtectorate.CODEC, PlayerOwnedProtectorate::new)
	);

	public static void init() {

	}

	private static <T extends ZoneData> ZoneDataType<T> register(String id, ZoneDataType<T> type) {
		return Registry.register(ZoneDataRegistry.REGISTRY, METAcraftPlots.getID(id), type);
	}

}
