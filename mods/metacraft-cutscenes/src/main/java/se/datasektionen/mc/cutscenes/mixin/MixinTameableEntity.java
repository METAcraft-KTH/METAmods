package se.datasektionen.mc.cutscenes.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.EntityView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import se.datasektionen.mc.cutscenes.cutscene.world.CutsceneWorld;
import se.datasektionen.mc.cutscenes.util.helper.CutsceneHelper;

@Mixin(TameableEntity.class)
public abstract class MixinTameableEntity {

	@Shadow public abstract EntityView getWorld();

	@WrapOperation(
		method = "cannotFollowOwner",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/entity/LivingEntity;isSpectator()Z"
			)
	)
	public boolean cannotFollowOwner(LivingEntity owner, Operation<Boolean> original) {
		if (owner instanceof ServerPlayerEntity player) {
			var scene = CutsceneHelper.getCutscene(player);
			if (scene.isPresent()) {
				if (this.getWorld() != scene.get().getCutsceneWorld()) return true;
			} else {
				if (this.getWorld() instanceof CutsceneWorld) return true;
			}
		}
		return original.call(owner);
	}

}
