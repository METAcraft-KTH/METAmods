package nu.metacraft.core.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import nu.metacraft.core.extensions.BlockEntityExtensions;
import nu.metacraft.core.extensions.ServerPlayerEntityExtensions;

@Mixin(BlockItem.class)
public class MixinBlockItem {

	@Inject(
		method = "writeNbtToBlockEntity",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/component/type/NbtComponent;applyToBlockEntity(Lnet/minecraft/block/entity/BlockEntity;Lnet/minecraft/registry/RegistryWrapper$WrapperLookup;)Z"
		)
	)
	private static void writeNbtToBlockEntity(
			World world, PlayerEntity player, BlockPos pos, ItemStack stack,
			CallbackInfoReturnable<Boolean> cir
	) {
		var tile = world.getBlockEntity(pos);
		if (tile != null) {
			((BlockEntityExtensions) tile).metacraft_core$setMovable(((ServerPlayerEntityExtensions) player).metacraft_core$areBlocksPistonMovable());
		}
	}

}
