package nu.metacraft.lib.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Ghast.class)
public class MixinGhastEntity {

	@Unique
	private static final String IGNORE_Y_CHECK = "IgnoreYCheck";


	@Unique
	private static final String PREVENT_RETURN_INSTAKILL = "PreventReturnInstakill";


	@Unique
	private boolean ignoreYCheck = false;

	@Unique
	private boolean preventReturnInstakill = false;


	@Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
	public void writeNBT(ValueOutput nbt, CallbackInfo ci) {
		nbt.putBoolean(IGNORE_Y_CHECK, ignoreYCheck);
		nbt.putBoolean(PREVENT_RETURN_INSTAKILL, preventReturnInstakill);
	}

	@Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
	public void readNBT(ValueInput nbt, CallbackInfo ci) {
		ignoreYCheck = nbt.getBooleanOr(IGNORE_Y_CHECK, false);
		preventReturnInstakill = nbt.getBooleanOr(PREVENT_RETURN_INSTAKILL, false);
	}

	@ModifyExpressionValue(
			method = "method_18450",
			at = @At(
				value = "CONSTANT",
				args = "doubleValue=4.0"
			)
	)
	public double yCheck(double original) {
		if (ignoreYCheck) {
			return Double.POSITIVE_INFINITY;
		}
		return original;
	}

	@ModifyExpressionValue(
		method = "hurtServer",
		at = @At(
			value = "CONSTANT",
			args = "floatValue=1000.0f"
		)
	)
	public float modifyInstakillDamage(float instakillAmount, ServerLevel world, DamageSource source, float amount) {
		if (preventReturnInstakill) {
			return amount;
		}
		return instakillAmount;
	}

}
