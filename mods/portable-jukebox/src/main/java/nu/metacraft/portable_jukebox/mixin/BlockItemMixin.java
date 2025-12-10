package nu.metacraft.portable_jukebox.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import nu.metacraft.lib.util.EntityRef;
import nu.metacraft.portable_jukebox.block.PortableJukeboxBlock;
import nu.metacraft.portable_jukebox.entity.PortableJukeboxEntity;

@Mixin(BlockItem.class)
public abstract class BlockItemMixin {

	@Shadow public abstract Block getBlock();

	@Inject(
		method = "place(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/InteractionResult;",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/Block;setPlacedBy(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)V"
		)
	)
	public void onPlaced(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
		if (this.getBlock() instanceof PortableJukeboxBlock) return;
		if (!(this.getBlock() instanceof EntityBlock)) return;
		if (context.getPlayer() != null) {
			var blockEntity = context.getLevel().getBlockEntity(context.getClickedPos());
			if (blockEntity != null) {
				PortableJukeboxEntity.transfer(
						EntityRef.fromEntity(context.getPlayer()), EntityRef.fromBlock(blockEntity), context.getItemInHand()
				);
			}
		}
	}

}
