package nu.metacraft.cutscenes.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import nu.metacraft.cutscenes.cutscene.CutsceneInstance;
import nu.metacraft.cutscenes.util.helper.CutsceneHelper;

@Mixin(PlayerChunkSender.class)
public class MixinChunkDataSender {

	@ModifyVariable(
			method = "sendChunk", at = @At("HEAD"),
			argsOnly = true
	)
	private static LevelChunk sendChunkData(LevelChunk chunk, ServerGamePacketListenerImpl handler) {
		return CutsceneHelper.getCutscene(handler.player).flatMap(
				scene -> scene.getCutsceneWorld().getChunkFromCacheIfPresent(chunk)
		).orElse(chunk);
	}

	@ModifyVariable(
			method = "sendChunk", at = @At("HEAD"),
			argsOnly = true
	)
	private static ServerLevel sendChunkData(
			ServerLevel world, ServerGamePacketListenerImpl handler,
			@Local(argsOnly = true) LevelChunk chunk
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
