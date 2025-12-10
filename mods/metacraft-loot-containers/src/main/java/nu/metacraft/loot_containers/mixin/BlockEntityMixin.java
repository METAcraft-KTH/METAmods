package nu.metacraft.loot_containers.mixin;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import nu.metacraft.loot_containers.containers.LootContainerData;

@Mixin(BlockEntity.class)
public class BlockEntityMixin {

	@Shadow @Nullable protected Level level;

	@Shadow @Final protected BlockPos worldPosition;

	@Inject(method = "setRemoved", at = @At("HEAD"))
	public void markRemoved(CallbackInfo ci) {
		if (level != null && !level.isClientSide() && level.hasChunkAt(worldPosition)) {
			LootContainerData.getInstance(level.getServer()).removeLootContainers(
					level.dimension(), worldPosition
			);
		}
	}

}
