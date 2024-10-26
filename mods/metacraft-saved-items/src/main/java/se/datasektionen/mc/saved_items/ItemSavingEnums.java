package se.datasektionen.mc.saved_items;

import com.chocohead.mm.api.ClassTinkerers;
import com.mojang.datafixers.DSL;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;

public class ItemSavingEnums implements Runnable {

	protected static final String SAVED_DATA_SAVED_ITEMS = "METACRAFT_SAVED_DATA_SAVED_ITEMS";

	@Override
	public void run() {
		MappingResolver remapper = FabricLoader.getInstance().getMappingResolver();

		ClassTinkerers.enumBuilder(
				remapper.mapClassName(
						"named",
						"net.minecraft.datafixer.DataFixTypes"
				),
				DSL.TypeReference.class
		).addEnum(
				SAVED_DATA_SAVED_ITEMS,
				SavedItemsDataFixer.SAVED_DATA_SAVED_ITEMS
		).build();
	}
}
