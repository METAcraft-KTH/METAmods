package nu.metacraft.lib.util;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import nu.metacraft.lib.extensions.ServerLevelExtensions;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public class SavedDataTypeCache {

	public static <T extends SavedData> SavedDataType<@NotNull T> get(MinecraftServer server, Type<T> type) {
		return get(server.overworld(), type);
	}

	public static <T extends SavedData> SavedDataType<@NotNull T> get(ServerLevel level, Type<T> type) {
		return ((ServerLevelExtensions) level).metacraft$getSavedDataType(type);
	}

	public record Type<T extends SavedData>(Function<ServerLevel, SavedDataType<@NotNull T>> createSavedDataType) {}

}
