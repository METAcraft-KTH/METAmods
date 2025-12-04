package nu.metacraft.lib.util.helper;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.DialogTags;

public class DialogHelper {

	public enum QuickActionsCount {
		NONE, SINGLE, MULTIPLE
	}

	public static QuickActionsCount getQuickActionsCount(HolderLookup.Provider lookup) {
		var tags = lookup.get(Registries.DIALOG).orElseThrow().value().getTagOrEmpty(
				DialogTags.QUICK_ACTIONS
		).iterator();
		QuickActionsCount count = QuickActionsCount.NONE;
		if (tags.hasNext()) {
			count = QuickActionsCount.SINGLE;
			tags.next();
		}
		if (tags.hasNext()) {
			count = QuickActionsCount.MULTIPLE;
		}
		return count;
	}

	public static boolean hasManyQuickActions(HolderLookup.Provider lookup) {
		return getQuickActionsCount(lookup) == QuickActionsCount.MULTIPLE;
	}

}
