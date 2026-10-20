package metacraft.moredyes.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import metacraft.moredyes.sign.SignColors;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.UnaryOperator;

/** A sign edited by a player keeps the colour of ours its lines were in (see {@link SignColors}). */
@Mixin(SignBlockEntity.class)
public abstract class SignBlockEntityMixin {
	@WrapOperation(method = "updateSignText", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/entity/SignBlockEntity;updateText(Ljava/util/function/UnaryOperator;Lnet/minecraft/world/level/block/entity/SignTextSlot;)Z"))
	private boolean moredyes$keepOurColour(SignBlockEntity self, UnaryOperator<SignText> edit, SignTextSlot slot, Operation<Boolean> original) {
		return original.call(self, (UnaryOperator<SignText>) before -> SignColors.keep(before, edit.apply(before)), slot);
	}
}
