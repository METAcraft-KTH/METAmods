package nu.metacraft.core.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import nu.metacraft.core.extensions.BlockEntityExtensions;
import nu.metacraft.core.extensions.ServerPlayerEntityExtensions;

@Mixin(BlockItem.class)
public class MixinBlockItem {

	@Inject(
		method = "updateCustomBlockEntityTag(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)Z",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/item/component/TypedEntityData;loadInto(Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/core/HolderLookup$Provider;)Z"
		)
	)
	private static void writeNbtToBlockEntity(
			Level world, Player player, BlockPos pos, ItemStack stack,
			CallbackInfoReturnable<Boolean> cir
	) {
		var tile = world.getBlockEntity(pos);
		if (tile != null) {
			((BlockEntityExtensions) tile).metacraft_core$setMovable(((ServerPlayerEntityExtensions) player).metacraft_core$areBlocksPistonMovable());
		}
	}

}
