package se.datasektionen.mc.portable_jukebox.mixin;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.util.ActionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import se.datasektionen.mc.metacraft_lib.util.EntityRef;
import se.datasektionen.mc.portable_jukebox.block.PortableJukeboxBlock;
import se.datasektionen.mc.portable_jukebox.entity.PortableJukeboxEntity;

@Mixin(BlockItem.class)
public abstract class MixinBlockItem {

	@Shadow public abstract Block getBlock();

	@Inject(
		method = "place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/block/Block;onPlaced(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;)V"
		)
	)
	public void onPlaced(ItemPlacementContext context, CallbackInfoReturnable<ActionResult> cir) {
		if (this.getBlock() instanceof PortableJukeboxBlock) return;
		if (!(this.getBlock() instanceof BlockEntityProvider)) return;
		if (context.getPlayer() != null) {
			var blockEntity = context.getWorld().getBlockEntity(context.getBlockPos());
			if (blockEntity != null) {
				PortableJukeboxEntity.transfer(
						EntityRef.fromEntity(context.getPlayer()), EntityRef.fromBlock(blockEntity), context.getStack()
				);
			}
		}
	}

}
