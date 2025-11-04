package nu.metacraft.dungeons.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.chunk.status.ChunkStatusTasks;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ChunkStatusTasks.class)
public class MixinChunkGenerating {


	@WrapOperation(
		method = {"generateBiomes", "generateNoise"}, at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/level/levelgen/blending/Blender;of(Lnet/minecraft/server/level/WorldGenRegion;)Lnet/minecraft/world/level/levelgen/blending/Blender;"
		)
	)
	private static Blender skipBlendingInSuperflatWorlds(
			WorldGenRegion chunkRegion, Operation<Blender> original, @Local(argsOnly = true) WorldGenContext context
	) { //The blender adds ~30 second lag spikes when generating dungeons, despite never being used.
		return context.generator().getClass().equals(FlatLevelSource.class) ? Blender.empty() : original.call(chunkRegion);
	}

}
