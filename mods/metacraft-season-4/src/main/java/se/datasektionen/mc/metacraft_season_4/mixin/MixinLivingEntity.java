package se.datasektionen.mc.metacraft_season_4.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.tag.EntityTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import se.datasektionen.mc.metacraft_season_4.end.EndBossPlayerState;
import se.datasektionen.mc.metacraft_season_4.extensions.SurviveDeathExtension;
import se.datasektionen.mc.metacraft_season_4.extensions.DragExtension;
import se.datasektionen.mc.metacraft_season_4.extensions.LivingEntityExtensionsInternal;
import se.datasektionen.mc.metacraft_season_4.status_effects.Season4StatusEffects;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity extends Entity implements LivingEntityExtensionsInternal, DragExtension, SurviveDeathExtension {

	@Shadow public abstract boolean canFreeze();

	@Unique
	private float prevDamageAmount;

	public MixinLivingEntity(EntityType<?> type, World world) {
		super(type, world);
	}

	@ModifyReturnValue(
		method = "computeFallDamage",
		at = @At("RETURN")
	)
	public int computeFallDamage(int original) {
		if ((Object) this instanceof ServerPlayerEntity player) {
			return EndBossPlayerState.getInstance(player.getServerWorld()).filter(
					state -> state.isBoss(player)
			).map(s -> 0).orElse(original);
		}
		return original;
	}

	@Inject(method = "applyDamage", at = @At("HEAD"))
	public void applyDamage(ServerWorld world, DamageSource source, float amount, CallbackInfo ci) {
		prevDamageAmount = 0;
	}

	@ModifyArg(
		method = "applyDamage",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/entity/damage/DamageTracker;onDamage(Lnet/minecraft/entity/damage/DamageSource;F)V"
		),
		index = 1
	)
	public float applyDamage(float damage) {
		this.prevDamageAmount = damage;
		return damage;
	}

	@ModifyVariable(
		method = "travelMidAir",
		slice = @Slice(
				from = @At(
						value = "INVOKE",
						target = "Lnet/minecraft/entity/LivingEntity;hasNoDrag()Z"
				)
		),
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/entity/LivingEntity;setVelocity(DDD)V",
			ordinal = 1
		),
		ordinal = 2
	)
	private float travelMidAir(float par1) {
		return metacraft_season_4$updateYDrag(par1);
	}

	@Override
	public float metacraft_season_4$getPrevDamageAmount() {
		return prevDamageAmount;
	}

	@Override
	public void metacraft_season_4$setPrevDamageAmount(float prevDamageAmount) {
		this.prevDamageAmount = prevDamageAmount;
	}

	@Inject(method = "canHaveStatusEffect", at = @At("HEAD"), cancellable = true)
	public void canHaveStatusEffect(
			StatusEffectInstance effect, CallbackInfoReturnable<Boolean> cir
	) {
		if (effect.getEffectType() == Season4StatusEffects.FIRE && this.isFireImmune()) {
			cir.setReturnValue(false);
		}
		if (effect.getEffectType() == Season4StatusEffects.FREEZE && this.getType().isIn(EntityTypeTags.FREEZE_IMMUNE_ENTITY_TYPES)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "tryUseDeathProtector", at = @At("HEAD"), cancellable = true)
	private void tryUseDeathProtector(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
		if (metacraft_season_4$surviveDeath(source)) {
			cir.setReturnValue(true);
		}
	}
}
