package se.datasektionen.mc.metacraft_lib.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.GhastEntity;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GhastEntity.class)
public class MixinGhastEntity {

	@Unique
	private static final String IGNORE_Y_CHECK = "IgnoreYCheck";


	@Unique
	private static final String PREVENT_RETURN_INSTAKILL = "PreventReturnInstakill";


	@Unique
	private boolean ignoreYCheck = false;

	@Unique
	private boolean preventReturnInstakill = false;


	@Inject(method = "writeCustomDataToNbt", at = @At("RETURN"))
	public void writeNBT(NbtCompound nbt, CallbackInfo ci) {
		nbt.putBoolean(IGNORE_Y_CHECK, ignoreYCheck);
		nbt.putBoolean(PREVENT_RETURN_INSTAKILL, preventReturnInstakill);
	}

	@Inject(method = "readCustomDataFromNbt", at = @At("RETURN"))
	public void readNBT(NbtCompound nbt, CallbackInfo ci) {
		ignoreYCheck = nbt.getBoolean(IGNORE_Y_CHECK);
		preventReturnInstakill = nbt.getBoolean(PREVENT_RETURN_INSTAKILL);
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
		method = "damage",
		at = @At(
			value = "CONSTANT",
			args = "floatValue=1000.0f"
		)
	)
	public float modifyInstakillDamage(float instakillAmount, DamageSource source, float amount) {
		if (preventReturnInstakill) {
			return amount;
		}
		return instakillAmount;
	}

}
