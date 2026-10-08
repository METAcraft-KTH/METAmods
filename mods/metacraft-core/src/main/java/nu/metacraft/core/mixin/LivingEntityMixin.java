package nu.metacraft.core.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import nu.metacraft.core.block.blocks.PortalCore;
import nu.metacraft.core.block.blocks.PortalPadding;
import nu.metacraft.core.block.entities.PortalEntity;
import nu.metacraft.core.status_effects.METAcraftEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity {

	public LivingEntityMixin(EntityType<?> type, Level world) {
		super(type, world);
	}

	@Inject(method = "canBeAffected", at = @At("HEAD"), cancellable = true)
	public void canHaveStatusEffect(
			MobEffectInstance effect, CallbackInfoReturnable<Boolean> cir
	) {
		if (effect.getEffect() == METAcraftEffects.FIRE && this.fireImmune()) {
			cir.setReturnValue(false);
		}
		if (effect.getEffect() == METAcraftEffects.FREEZE && this.is(EntityTypeTags.FREEZE_IMMUNE_ENTITY_TYPES)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "handleFallFlyingCollisions", at = @At("HEAD"), cancellable = true)
	private void handleFallFlyingCollisions(double moveHorLength, double newMoveHorLength, CallbackInfo ci) {
		for (var pos : BlockPos.betweenClosed(getBoundingBox())) {
			var block = level().getBlockState(pos).getBlock();
			if (
				block instanceof PortalEntity.ConnectedToPortalBlockEntity p &&
				p.getPortal(level(), pos).isPresent()
			) {
				ci.cancel();
				return;
			}
		}
		if (portalProcess != null) {
			if (
				((PortalProcessorAccessor) portalProcess).getPortal() instanceof PortalEntity.ConnectedToPortalBlockEntity p &&
				p.getPortal(level(), portalProcess.getEntryPosition()).isPresent()
			) {
				ci.cancel();
				return;
			}
		}
	}

}
