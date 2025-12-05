package nu.metacraft.lib.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import nu.metacraft.lib.extensions.EntityExtensions;
import nu.metacraft.lib.entity.EntityParameters;

@Mixin(Entity.class)
public abstract class EntityMixin implements EntityExtensions {


	@Shadow public abstract void removeVehicle();

	@Shadow @Nullable public abstract Entity getVehicle();

	@Shadow public abstract Component getName();

	@Shadow public abstract EntityType<?> getType();

	@Unique
	private boolean preventEnterVehicle = false;

	@Unique
	private boolean hideUUIDInTooltip = false;

	@Inject(method = "load", at = @At("RETURN"))
	public void readNBT(ValueInput nbt, CallbackInfo ci) {
		preventEnterVehicle = nbt.getBooleanOr(EntityParameters.PREVENT_ENTER_VEHICLE, false);
		hideUUIDInTooltip = nbt.getBooleanOr(EntityParameters.HIDE_UUID_TOOLTIP, false);
	}

	@Inject(method = "tick", at = @At("RETURN"))
	public void tick(CallbackInfo ci) {
		if (preventEnterVehicle && (this.getVehicle() instanceof Boat || this.getVehicle() instanceof AbstractMinecart)) {
			this.removeVehicle();
		}
	}

	@Inject(method = "saveWithoutId", at = @At("RETURN"))
	public void writeNBT(ValueOutput nbt, CallbackInfo ci) {
		nbt.putBoolean(EntityParameters.PREVENT_ENTER_VEHICLE, preventEnterVehicle);
		nbt.putBoolean(EntityParameters.HIDE_UUID_TOOLTIP, hideUUIDInTooltip);
	}

	@ModifyReturnValue(
			method = "getDisplayName",
			at = @At("RETURN")
	)
	public Component getDisplayName(Component text) {
		if (hideUUIDInTooltip) {
			return ((MutableComponent) text).withStyle(style -> style.withInsertion(null));
		}
		return text;
	}

	@Inject(
			method = "createHoverEvent",
			at = @At("HEAD"),
			cancellable = true
	)
	public void getHoverEvent(CallbackInfoReturnable<HoverEvent> cir) {
		if (hideUUIDInTooltip) {
			cir.setReturnValue(new HoverEvent.ShowText(
					Component.empty().append(
							this.getName()
					).append(
							Component.literal("\n")
					).append(
							Component.translatable("gui.entity_tooltip.type", this.getType().getDescription())
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
