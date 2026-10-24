package se.metacraft.moredyes.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import se.metacraft.moredyes.content.block.ColoredBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Lighting a candle plays the flint and steel to everyone but the player who lit it, whose own
 * client is expected to have played it. Ours look like a lantern or a cake to that client, which
 * plays nothing, so they get it from the server too.
 */
@Mixin(FlintAndSteelItem.class)
public abstract class FlintAndSteelItemMixin {
	@WrapOperation(method = "useOn", at = @At(value = "INVOKE", ordinal = 0,
			target = "Lnet/minecraft/world/level/Level;playSound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/BlockPos;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V"))
	private void moredyes$lighterHearsIt(Level level, Entity source, BlockPos pos, SoundEvent sound, SoundSource category,
			float volume, float pitch, Operation<Void> original) {
		Block block = level.getBlockState(pos).getBlock();
		boolean ours = block instanceof ColoredBlocks.Candle || block instanceof ColoredBlocks.CandleCake;
		original.call(level, ours ? null : source, pos, sound, category, volume, pitch);
	}
}
