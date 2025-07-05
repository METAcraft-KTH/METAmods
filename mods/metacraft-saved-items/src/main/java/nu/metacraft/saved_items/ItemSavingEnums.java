package nu.metacraft.saved_items;

import com.chocohead.mm.api.ClassTinkerers;
import com.mojang.datafixers.DSL;
import nu.metacraft.lib.util.EarlyClassNames;

public class ItemSavingEnums implements Runnable {

	protected static final String SAVED_DATA_SAVED_ITEMS = "METACRAFT_SAVED_DATA_SAVED_ITEMS";

	@Override
	public void run() {
		ClassTinkerers.enumBuilder(
				EarlyClassNames.DATA_FIX_TYPES,
				DSL.TypeReference.class
		).addEnum(
				SAVED_DATA_SAVED_ITEMS,
				SavedItemsDataFixer.SAVED_DATA_SAVED_ITEMS
		).build();
	}
}
