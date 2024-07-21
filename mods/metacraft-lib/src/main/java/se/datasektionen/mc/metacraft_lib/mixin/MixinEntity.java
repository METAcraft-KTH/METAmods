package se.datasektionen.mc.metacraft_lib.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.nbt.NbtCompound;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import se.datasektionen.mc.metacraft_lib.extensions.EntityExtensions;
import se.datasektionen.mc.metacraft_lib.entity.EntityParameters;

@Mixin(Entity.class)
public abstract class MixinEntity implements EntityExtensions {


	@Shadow public abstract void dismountVehicle();

	@Shadow @Nullable public abstract Entity getVehicle();

	@Unique
	private boolean preventEnterVehicle = false;

	@Inject(method = "readNbt", at = @At("RETURN"))
	public void readNBT(NbtCompound nbt, CallbackInfo ci) {
		preventEnterVehicle = nbt.getBoolean(EntityParameters.PREVENT_ENTER_VEHICLE);
	}

	@Inject(method = "tick", at = @At("RETURN"))
	public void tick(CallbackInfo ci) {
		if (preventEnterVehicle && (this.getVehicle() instanceof BoatEntity || this.getVehicle() instanceof AbstractMinecartEntity)) {
			this.dismountVehicle();
		}
	}

	@Inject(method = "writeNbt", at = @At("RETURN"))
	public void writeNBT(NbtCompound nbt, CallbackInfoReturnable<NbtCompound> cir) {
		if (nbt.contains(EntityParameters.PREVENT_ENTER_VEHICLE)) {
			nbt.putBoolean(EntityParameters.PREVENT_ENTER_VEHICLE, preventEnterVehicle);
		}
	}

	@Override
	public boolean metacraft_lib$preventEnterVehicle() {
		return preventEnterVehicle;
	}

}
