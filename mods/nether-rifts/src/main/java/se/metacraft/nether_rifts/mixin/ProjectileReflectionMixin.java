package se.metacraft.nether_rifts.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import se.metacraft.nether_rifts.mob.PiglinGeneral;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * What a spinning Piglin General turns back ({@link PiglinGeneral#REFLECTION}, through vanilla's per-entity
 * {@code deflection}, which every projectile's hit asks) becomes his ({@link PiglinGeneral#ownerOfReflected}): vanilla
 * gives a deflected projectile back to the owner it had, after the deflection runs. And it passes through him after,
 * rather than catching on his box as it leaves.
 */
@Mixin(Projectile.class)
public abstract class ProjectileReflectionMixin {

	@Shadow
	@Nullable
	private Entity lastDeflectedBy;

	@WrapOperation(method = "hitTargetOrDeflectSelf", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/projectile/Projectile;deflect(Lnet/minecraft/world/entity/projectile/ProjectileDeflection;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/EntityReference;ZD)Z"))
	private boolean netherRifts$generalOwnsWhatHeTurns(Projectile projectile, ProjectileDeflection deflection, @Nullable Entity by,
			@Nullable EntityReference<Entity> owner, boolean byAttack, double power, Operation<Boolean> deflect) {
		if (by instanceof PiglinGeneral general && deflection == PiglinGeneral.REFLECTION) {
			owner = general.ownerOfReflected(projectile, owner);
		}
		return deflect.call(projectile, deflection, by, owner, byAttack, power);
	}

	@Inject(method = "canHitEntity", at = @At("HEAD"), cancellable = true)
	private void netherRifts$passTheGeneralItTurned(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (entity instanceof PiglinGeneral && entity == lastDeflectedBy) {
			cir.setReturnValue(false);
		}
	}
}
