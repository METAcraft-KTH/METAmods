package nu.metacraft.lib.util.helper;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.DialogTags;

public class DialogHelper {

	public enum QuickActionsCount {
		NONE, SINGLE, MULTIPLE
	}

	public static QuickActionsCount getQuickActionsCount(HolderLookup.Provider lookup) {
		int count = (int) lookup.lookupOrThrow(Registries.DIALOG).get(
				DialogTags.QUICK_ACTIONS
		).orElseThrow().stream().limit(2).count();
		return switch (count) {
			case 0 -> QuickActionsCount.NONE;
			case 1 -> QuickActionsCount.SINGLE;
			default -> QuickActionsCount.MULTIPLE;
		};
	}

	public static boolean hasManyQuickActions(HolderLookup.Provider lookup) {
		return getQuickActionsCount(lookup) == QuickActionsCount.MULTIPLE;
	}

}
