package se.datasektionen.mc.metacraft_moderation.exile.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import se.datasektionen.mc.metacraft_moderation.exile.rules.PreventInteraction;

@Mixin(Entity.class)
public abstract class MixinEntity {

	@Shadow public abstract BlockPos getBlockPos();

	@Shadow @Nullable public abstract MinecraftServer getServer();

	@Shadow private World world;

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
					if (PreventInteraction.shouldCancelInteractionAt(
						this.getServer(), tamed.getOwnerUuid(), this.getBlockPos()
					)) {
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
