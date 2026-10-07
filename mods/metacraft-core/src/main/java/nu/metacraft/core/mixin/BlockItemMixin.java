package nu.metacraft.core.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import eu.pb4.polymer.core.api.item.PolymerItem;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.SoundType;
import nu.metacraft.core.util.ServerSoundType;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import nu.metacraft.core.extensions.BlockEntityExtensions;
import nu.metacraft.core.extensions.ServerPlayerExtensions;

@Mixin(BlockItem.class)
public class BlockItemMixin {

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
			((BlockEntityExtensions) tile).metacraft_core$setMovable(((ServerPlayerExtensions) player).metacraft_core$areBlocksPistonMovable());
		}
	}

	@ModifyArg(
		method = "place",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/level/Level;playSound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/BlockPos;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V"
		)
	)
	public @Nullable Entity sendPlaceSoundFromServer(
		@Nullable Entity except, @Local(name = "soundType") SoundType soundType
	) {
		//noinspection ConstantValue
		if (
			soundType instanceof ServerSoundType &&
			(this.getClass() == (Object) BlockItem.class || this instanceof PolymerItem)
		) return null;
		return except;
	}

}
