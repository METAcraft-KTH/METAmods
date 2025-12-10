package nu.metacraft.saved_items;

import com.chocohead.mm.api.ClassTinkerers;
import com.mojang.datafixers.DSL;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.datafix.fixes.References;

public class SavedItemsDataFixer {

	public static final DSL.TypeReference SAVED_DATA_SAVED_ITEMS = References.reference(
			"metacraft/saved_data/saved_items"
	);

	public static class Types {

		public static final DataFixTypes SAVED_DATA_SAVED_ITEMS = ClassTinkerers.getEnum(
				DataFixTypes.class, ItemSavingEnums.SAVED_DATA_SAVED_ITEMS
		);

	}

}
