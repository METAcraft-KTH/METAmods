package se.metacraft.playertrading.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.metacraft.playertrading.block.blocks.BaseShopBlock;
import se.metacraft.playertrading.block.entities.ShopBlockEntity;

@Mixin(Level.class)
public class LevelMixin {

	@Inject(method = "blockEntityChanged", at = @At("HEAD"))
	private void onBlockChanged(BlockPos pos, CallbackInfo ci) {
		BaseShopBlock.updateShopStash((Level) (Object) this, pos);
	}

}
