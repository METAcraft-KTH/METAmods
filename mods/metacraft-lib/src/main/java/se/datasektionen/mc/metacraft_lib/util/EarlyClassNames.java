package se.datasektionen.mc.metacraft_lib.util;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;

@SuppressWarnings("unused")
public class EarlyClassNames {

	private static final MappingResolver remapper = FabricLoader.getInstance().getMappingResolver();

	public static final String DATA_FIX_TYPES = remapper.mapClassName(
			"intermediary",
			IntermediaryNames.DATA_FIX_TYPES
	);

}
