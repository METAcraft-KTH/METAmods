package nu.metacraft.lib.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.entity.Visibility;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.SavedDataStorage;
import net.minecraft.world.level.storage.WritableLevelData;
import nu.metacraft.lib.extensions.ServerAndServerLevelExtensions;
import nu.metacraft.lib.util.SavedDataTypeCache;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.HashMap;
import java.util.Map;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin extends Level implements ServerAndServerLevelExtensions<ServerLevel> {

	@Shadow @Final private PersistentEntitySectionManager<Entity> entityManager;

	@Shadow
	public abstract SavedDataStorage getDataStorage();

	protected ServerLevelMixin(WritableLevelData properties, ResourceKey<Level> registryRef, RegistryAccess registryManager, Holder<DimensionType> dimensionEntry, boolean isClient, boolean debugWorld, long seed, int maxChainedNeighborUpdates) {
		super(properties, registryRef, registryManager, dimensionEntry, isClient, debugWorld, seed, maxChainedNeighborUpdates);
	}

	@ModifyExpressionValue(
		method = "lambda$waitForEntities$0",
		at = @At(
				value = "INVOKE",
				target = "Lnet/minecraft/server/level/ServerLevel;areEntitiesLoaded(J)Z"
		)
	)
	public boolean onLoadChunks(boolean original, @Local(name = "chunk") ChunkPos chunk) {
		if (!original) {
			this.entityManager.updateChunkStatus(chunk, Visibility.TRACKED);
		}
		return original;
	}

	@Unique
	private final Map<SavedDataTypeCache.Type<?, ServerLevel>, SavedDataType<?>> typeMap = new HashMap<>();

	@SuppressWarnings("unchecked")
	@Override
	public <T extends SavedData> SavedDataType<@NotNull T> metacraft$getSavedDataType(SavedDataTypeCache.Type<T, ServerLevel> type) {
		return (SavedDataType<@NotNull T>) typeMap.computeIfAbsent(
				type, t -> t.createSavedDataType().apply((ServerLevel) (Object) this)
		);
	}

}
