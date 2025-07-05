package nu.metacraft.cutscenes.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.network.ChunkDataSender;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import nu.metacraft.cutscenes.cutscene.CutsceneInstance;
import nu.metacraft.cutscenes.util.helper.CutsceneHelper;

@Mixin(ChunkDataSender.class)
public class MixinChunkDataSender {

	@ModifyVariable(
			method = "sendChunkData", at = @At("HEAD"),
			argsOnly = true
	)
	private static WorldChunk sendChunkData(WorldChunk chunk, ServerPlayNetworkHandler handler) {
		return CutsceneHelper.getCutscene(handler.player).flatMap(
				scene -> scene.getCutsceneWorld().getChunkFromCacheIfPresent(chunk)
		).orElse(chunk);
	}

	@ModifyVariable(
			method = "sendChunkData", at = @At("HEAD"),
			argsOnly = true
	)
	private static ServerWorld sendChunkData(
			ServerWorld world, ServerPlayNetworkHandler handler,
			@Local(argsOnly = true) WorldChunk chunk
	) {
		return CutsceneHelper.getCutscene(handler.player).map(
				CutsceneInstance::getCutsceneWorld
		).map(
				c -> {
					if (c.isLightingInCache(chunk.getPos())) {
						return c;
					} else {
						return world;
					}
				}
		).orElse(world);
	}

}
