package nu.metacraft.moderation.exile.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.level.Level;
import nu.metacraft.moderation.exile.rules.PreventInteraction;

@Mixin(Entity.class)
public abstract class EntityMixin {

	@Shadow public abstract BlockPos blockPosition();

	@Shadow private Level level;

	@Shadow public abstract Level level();

	@Inject(
		method = "isInvulnerableToBase",
		at = @At("HEAD"),
		cancellable = true
	)
	public void isInvulnerableTo(DamageSource damageSource, CallbackInfoReturnable<Boolean> cir) {
		if (!level.isClientSide()) {
			ServerPlayer player = null;
			if (damageSource.getEntity() instanceof ServerPlayer p) {
				player = p;
			} else if (damageSource.getEntity() instanceof TamableAnimal tamed) {
				if (tamed.getOwner() instanceof ServerPlayer p) {
					player = p;
				} else {
					if (
						tamed.getOwnerReference() != null &&
						level.getServer().services().nameToIdCache().get(tamed.getOwnerReference().getUUID()).isPresent() &&
						PreventInteraction.shouldCancelInteractionAt(
								this.level().getServer(), tamed.getOwnerReference().getUUID(), level().dimension(), this.blockPosition()
						)
					) {
						cir.setReturnValue(true);
						return;
					}
				}
			}
			if (player != null) {
				if (PreventInteraction.shouldCancelInteraction(player, this.blockPosition())) {
					cir.setReturnValue(true);
				}
			}
		}
	}

}
