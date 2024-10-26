package se.datasektionen.mc.saved_items;

import com.chocohead.mm.api.ClassTinkerers;
import com.mojang.datafixers.DSL;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.datafixer.TypeReferences;

public class SavedItemsDataFixer {

	public static final DSL.TypeReference SAVED_DATA_SAVED_ITEMS = TypeReferences.create(
			"metacraft/saved_data/saved_items"
	);

	public static class Types {

		public static final DataFixTypes SAVED_DATA_SAVED_ITEMS = ClassTinkerers.getEnum(
				DataFixTypes.class, ItemSavingEnums.SAVED_DATA_SAVED_ITEMS
		);

	}

}
