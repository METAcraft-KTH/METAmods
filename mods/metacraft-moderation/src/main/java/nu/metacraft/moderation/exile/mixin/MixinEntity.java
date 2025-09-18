package nu.metacraft.moderation.exile.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import nu.metacraft.moderation.exile.rules.PreventInteraction;

@Mixin(Entity.class)
public abstract class MixinEntity {

	@Shadow public abstract BlockPos getBlockPos();

	@Shadow private World world;

	@Shadow public abstract World getEntityWorld();

	@Inject(
		method = "isAlwaysInvulnerableTo",
		at = @At("HEAD"),
		cancellable = true
	)
	public void isInvulnerableTo(DamageSource damageSource, CallbackInfoReturnable<Boolean> cir) {
		if (!world.isClient()) {
			ServerPlayerEntity player = null;
			if (damageSource.getAttacker() instanceof ServerPlayerEntity p) {
				player = p;
			} else if (damageSource.getAttacker() instanceof TameableEntity tamed) {
				if (tamed.getOwner() instanceof ServerPlayerEntity p) {
					player = p;
				} else {
					if (
						tamed.getOwnerReference() != null &&
						world.getServer().getApiServices().nameToIdCache().getByUuid(tamed.getOwnerReference().getUuid()).isPresent() &&
						PreventInteraction.shouldCancelInteractionAt(
								this.getEntityWorld().getServer(), tamed.getOwnerReference().getUuid(), getEntityWorld().getRegistryKey(), this.getBlockPos()
						)
					) {
						cir.setReturnValue(true);
						return;
					}
				}
			}
			if (player != null) {
				if (PreventInteraction.shouldCancelInteraction(player, this.getBlockPos())) {
					cir.setReturnValue(true);
				}
			}
		}
	}

}
