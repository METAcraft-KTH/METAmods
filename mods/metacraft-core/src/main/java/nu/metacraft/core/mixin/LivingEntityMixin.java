package nu.metacraft.core.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import nu.metacraft.core.block.entities.PortalEntity;
import nu.metacraft.core.status_effects.METAcraftEffects;
import nu.metacraft.core.util.ServerSoundType;
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

	@WrapOperation(
		method = "playBlockFallSound",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/LivingEntity;playSound(Lnet/minecraft/sounds/SoundEvent;FF)V"
		)
	)
	protected void fixFalLSound(
		LivingEntity instance, SoundEvent soundEvent, float volume, float pitch, Operation<Void> original,
		@Local(name = "soundType") SoundType soundType
	) {
		if ((Object) this instanceof Player && soundType instanceof ServerSoundType) {
			this.level().playSound(null, this.getX(), this.getY(), this.getZ(), soundEvent, this.getSoundSource(), volume, pitch);
		} else {
			original.call(instance, soundEvent, volume, pitch);
		}
	}

}
