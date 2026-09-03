package nu.metacraft.lib.extensions;

import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import nu.metacraft.lib.util.SavedDataTypeCache;
import org.jetbrains.annotations.NotNull;

public interface ServerAndServerLevelExtensions<L> {

	<T extends SavedData> SavedDataType<@NotNull T> metacraft$getSavedDataType(SavedDataTypeCache.Type<T, L> type);

}
