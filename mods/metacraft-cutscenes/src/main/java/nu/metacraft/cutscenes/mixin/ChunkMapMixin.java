package nu.metacraft.cutscenes.mixin;

import com.google.common.collect.ImmutableList;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import nu.metacraft.cutscenes.util.helper.CutsceneHelper;
import nu.metacraft.cutscenes.util.helper.HiddenEntityHelper;

@Mixin(ChunkMap.class)
public class ChunkMapMixin {

	@WrapOperation(
		method = "getPlayers(Lnet/minecraft/world/level/ChunkPos;Z)Ljava/util/List;",
		at = @At(
				value = "INVOKE",
				target = "Lcom/google/common/collect/ImmutableList$Builder;add(Ljava/lang/Object;)Lcom/google/common/collect/ImmutableList$Builder;"
		)
	)
	public <E> ImmutableList.Builder<@NotNull E> getPlayersWatchingChunk(
			ImmutableList.Builder<@NotNull E> instance, E element,
			Operation<ImmutableList.Builder<@NotNull E>> original
	) {
		if (CutsceneHelper.isInCutscene(((ServerPlayer) element))) {
			return instance;
		}
		return original.call(instance, element);
	}

	@Mixin(ChunkMap.TrackedEntity.class)
	public static class TrackedEntity {
		@Shadow @Final private Entity entity;

		@WrapOperation(
			method = "updatePlayer(Lnet/minecraft/server/level/ServerPlayer;)V",
			at = @At(
				value = "INVOKE",
				target = "Lnet/minecraft/server/level/ChunkMap;isChunkTracked(Lnet/minecraft/server/level/ServerPlayer;II)Z"
			)
		)
		public boolean updateTrackingStatus(
				ChunkMap manager, ServerPlayer player,
				int chunkX, int chunkZ, Operation<Boolean> original
		) {
			if (HiddenEntityHelper.isHiddenFrom(entity, player)) {
				return false;
			}
			return original.call(manager, player, chunkX, chunkZ);
		}
	}

}
