package se.metacraft.moredyes.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import se.metacraft.moredyes.sign.SignColors;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.UnaryOperator;

/**
 * A vanilla dye on a sign in one of our colours: vanilla only sets the sign's own colour, which our
 * lines' colour hides, and does nothing at all when that is already the dye's (the vanilla colour
 * standing in for ours). Taking our colour off the lines first makes the dye show either way.
 */
@Mixin(DyeItem.class)
public abstract class DyeItemMixin {
	@WrapOperation(method = "tryApplyToSign", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/entity/SignBlockEntity;updateText(Ljava/util/function/UnaryOperator;Lnet/minecraft/world/level/block/entity/SignTextSlot;)Z"))
	private boolean moredyes$unpaintFirst(SignBlockEntity sign, UnaryOperator<SignText> edit, SignTextSlot slot, Operation<Boolean> original) {
		return original.call(sign, (UnaryOperator<SignText>) before -> edit.apply(SignColors.unpaint(before)), slot);
	}
}
