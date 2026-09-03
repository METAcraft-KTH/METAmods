package nu.metacraft.lib.util;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import nu.metacraft.lib.extensions.ServerAndServerLevelExtensions;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public class SavedDataTypeCache {

	public static <T extends SavedData> SavedDataType<@NotNull T> get(MinecraftServer server, Type<T, MinecraftServer> type) {
		//noinspection unchecked
		return ((ServerAndServerLevelExtensions<MinecraftServer>) server).metacraft$getSavedDataType(type);
	}

	public static <T extends SavedData> SavedDataType<@NotNull T> get(ServerLevel level, Type<T, ServerLevel> type) {
		//noinspection unchecked
		return ((ServerAndServerLevelExtensions<ServerLevel>) level).metacraft$getSavedDataType(type);
	}

	public record Type<T extends SavedData, L>(Function<L, SavedDataType<@NotNull T>> createSavedDataType) {}

}
