package se.datasektionen.mc.metacraft_dungeons.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.chunk.ChunkGenerating;
import net.minecraft.world.chunk.ChunkGenerationContext;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.FlatChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ChunkGenerating.class)
public class MixinChunkGenerating {


	@WrapOperation(
		method = {"populateBiomes", "populateNoise"}, at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/gen/chunk/Blender;getBlender(Lnet/minecraft/world/ChunkRegion;)Lnet/minecraft/world/gen/chunk/Blender;"
		)
	)
	private static Blender skipBlendingInSuperflatWorlds(
			ChunkRegion chunkRegion, Operation<Blender> original, @Local(argsOnly = true) ChunkGenerationContext context
	) { //The blender adds ~30 second lag spikes when generating dungeons, despite never being used.
		return context.generator().getClass().equals(FlatChunkGenerator.class) ? Blender.getNoBlending() : original.call(chunkRegion);
	}

}
