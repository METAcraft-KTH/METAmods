package se.datasektionen.mc.loot_containers.mixin;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.datasektionen.mc.loot_containers.containers.LootContainerData;

@Mixin(BlockEntity.class)
public class MixinBlockEntity {

	@Shadow @Nullable protected World world;

	@Shadow @Final protected BlockPos pos;

	@Inject(method = "markRemoved", at = @At("HEAD"))
	public void markRemoved(CallbackInfo ci) {
		if (world != null && !world.isClient() && world.isChunkLoaded(pos)) {
			LootContainerData.getInstance(world.getServer()).removeLootContainers(
					world.getRegistryKey(), pos
			);
		}
	}

}
