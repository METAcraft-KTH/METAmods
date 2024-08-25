package se.datasektionen.mc.cutscenes.mixin;

import com.google.common.collect.ImmutableList;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerChunkLoadingManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import se.datasektionen.mc.cutscenes.util.helper.CutsceneHelper;

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

}
