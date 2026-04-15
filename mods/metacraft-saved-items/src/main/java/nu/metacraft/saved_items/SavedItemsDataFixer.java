package nu.metacraft.saved_items;

import com.mojang.datafixers.DSL;
import net.minecraft.util.datafix.fixes.References;

public class SavedItemsDataFixer {

	public static final DSL.TypeReference SAVED_DATA_SAVED_ITEMS = References.reference(
			"metacraft/saved_data/saved_items"
	);

}
