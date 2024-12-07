package se.datasektionen.mc.cutscenes.mixin;

import net.minecraft.server.network.ChunkDataSender;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import se.datasektionen.mc.cutscenes.util.helper.CutsceneHelper;

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

}
