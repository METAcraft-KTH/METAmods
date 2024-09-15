package se.datasektionen.mc.cutscenes.mixin;

import com.google.common.collect.ImmutableList;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerChunkLoadingManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import se.datasektionen.mc.cutscenes.util.helper.CutsceneHelper;
import se.datasektionen.mc.cutscenes.util.helper.HiddenEntityHelper;

@Mixin(ServerChunkLoadingManager.class)
public class MixinServerChunkLoadingManager {

	@WrapWithCondition(
		method = "getPlayersWatchingChunk(Lnet/minecraft/util/math/ChunkPos;Z)Ljava/util/List;",
		at = @At(
				value = "INVOKE",
				target = "Lcom/google/common/collect/ImmutableList$Builder;add(Ljava/lang/Object;)Lcom/google/common/collect/ImmutableList$Builder;"
		)
	)
	public boolean getPlayersWatchingChunk(ImmutableList.Builder<ServerPlayerEntity> instance, Object element) {
		if (CutsceneHelper.isInCutscene(((ServerPlayerEntity) element))) {
			return false;
		}
		return true;
	}

	@Mixin(ServerChunkLoadingManager.EntityTracker.class)
	public static class EntityTracker {
		@Shadow @Final private Entity entity;

		@WrapOperation(
			method = "updateTrackedStatus(Lnet/minecraft/server/network/ServerPlayerEntity;)V",
			at = @At(
				value = "INVOKE",
				target = "Lnet/minecraft/server/world/ServerChunkLoadingManager;isTracked(Lnet/minecraft/server/network/ServerPlayerEntity;II)Z"
			)
		)
		public boolean updateTrackingStatus(
				ServerChunkLoadingManager manager, ServerPlayerEntity player,
				int chunkX, int chunkZ, Operation<Boolean> original
		) {
			if (HiddenEntityHelper.isHiddenFrom(entity, player)) {
				return false;
			}
			return original.call(manager, player, chunkX, chunkZ);
		}
	}

}
