package se.datasektionen.mc.metacraft_lib.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
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

	@Shadow public abstract Text getName();

	@Shadow public abstract EntityType<?> getType();

	@Unique
	private boolean preventEnterVehicle = false;

	@Unique
	private boolean hideUUIDInTooltip = false;

	@Inject(method = "readData", at = @At("RETURN"))
	public void readNBT(ReadView nbt, CallbackInfo ci) {
		preventEnterVehicle = nbt.getBoolean(EntityParameters.PREVENT_ENTER_VEHICLE, false);
		hideUUIDInTooltip = nbt.getBoolean(EntityParameters.HIDE_UUID_TOOLTIP, false);
	}

	@Inject(method = "tick", at = @At("RETURN"))
	public void tick(CallbackInfo ci) {
		if (preventEnterVehicle && (this.getVehicle() instanceof BoatEntity || this.getVehicle() instanceof AbstractMinecartEntity)) {
			this.dismountVehicle();
		}
	}

	@Inject(method = "writeData", at = @At("RETURN"))
	public void writeNBT(WriteView nbt, CallbackInfo ci) {
		nbt.putBoolean(EntityParameters.PREVENT_ENTER_VEHICLE, preventEnterVehicle);
		nbt.putBoolean(EntityParameters.HIDE_UUID_TOOLTIP, hideUUIDInTooltip);
	}

	@ModifyReturnValue(
			method = "getDisplayName",
			at = @At("RETURN")
	)
	public Text getDisplayName(Text text) {
		if (hideUUIDInTooltip) {
			return ((MutableText) text).styled(style -> style.withInsertion(null));
		}
		return text;
	}

	@Inject(
			method = "getHoverEvent",
			at = @At("HEAD"),
			cancellable = true
	)
	public void getHoverEvent(CallbackInfoReturnable<HoverEvent> cir) {
		if (hideUUIDInTooltip) {
			cir.setReturnValue(new HoverEvent.ShowText(
					Text.empty().append(
							this.getName()
					).append(
							Text.literal("\n")
					).append(
							Text.translatable("gui.entity_tooltip.type", this.getType().getName())
					)
			));
		}
	}

	@Override
	public boolean metacraft_lib$preventEnterVehicle() {
		return preventEnterVehicle;
	}

	@Override
	public void metacraft_lib$setPreventEnterVehicle(boolean preventEnterVehicle) {
		this.preventEnterVehicle = preventEnterVehicle;
	}

	@Override
	public void metacraft_lib$setHideUUIDInTooltip(boolean hideUUIDInTooltip) {
		this.hideUUIDInTooltip = hideUUIDInTooltip;
	}

}
