package nu.metacraft.lib.util;

import net.minecraft.resources.Identifier;

public interface CustomAdvancementTracker {

	Identifier getType();

	default boolean canReceiveCopy() {
		return true;
	}

}
