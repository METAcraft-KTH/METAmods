package nu.metacraft.cutscenes.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.EntityTrackerEntry;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;
import nu.metacraft.cutscenes.extension.EntityExtension;

@Mixin(EntityTrackerEntry.class)
public class MixinEntityTrackerEntry {

	@Shadow @Final private Entity entity;

	@ModifyExpressionValue(
			method = "tick",
			at = @At(
					value = "FIELD",
					target = "Lnet/minecraft/server/network/EntityTrackerEntry;hadVehicle:Z",
					ordinal = 0
			),
			slice = @Slice(
					from = @At(
							value = "INVOKE",
							target = "Lnet/minecraft/entity/TrackedPosition;getDeltaX(Lnet/minecraft/util/math/Vec3d;)J"
					)
			)
	)
	public boolean shouldMoveAccurately(boolean original) {
		if (entity instanceof EntityExtension e && e.metacraft$hasAccurateMovement()) {
			return true;
		}
		return original;
	}

}
