package se.datasektionen.mc.better_pets.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.AnimalMateGoal;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.ParrotEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import se.datasektionen.mc.better_pets.TameableExtension;

@Mixin(ParrotEntity.class)
public abstract class MixinParrotEntity extends TameableEntity {

	@Shadow public abstract ParrotEntity.Variant getVariant();

	protected MixinParrotEntity(EntityType<? extends TameableEntity> entityType, World world) {
		super(entityType, world);
	}

	@ModifyExpressionValue(
			method = "interactMob",
			at = @At(
					value = "INVOKE", target = "Lnet/minecraft/entity/passive/ParrotEntity;isOwner(Lnet/minecraft/entity/LivingEntity;)Z"
			)
	)
	public boolean isTrusted(
			boolean original, @Local(argsOnly = true) PlayerEntity player, @Local(argsOnly = true) Hand hand, @Share("notOwner") LocalBooleanRef notOwner
	) {
		if (original) {
			return true;
		} else if (hand == Hand.MAIN_HAND) { //Check for main hand to prevent double interactions.
			notOwner.set(((TameableExtension) this).metaraft$isTrusted(player));
			return notOwner.get();
		}
		return false;
	}

	@ModifyArg(
			method = "interactMob",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/util/ActionResult;success(Z)Lnet/minecraft/util/ActionResult;"
			)
	)
	public boolean swingArm(boolean swingHand, @Share("notOwner") LocalBooleanRef notOwner) {
		return swingHand || notOwner.get();
	}

	@ModifyReturnValue(method = "isBaby", at = @At("RETURN"))
	public boolean isBaby(boolean original) {
		return super.isBaby();
	}

	@Inject(method = "canBreedWith", at = @At("HEAD"), cancellable = true)
	public void canBreedWith(AnimalEntity other, CallbackInfoReturnable<Boolean> cir) {
		cir.setReturnValue(super.canBreedWith(other));
	}

	@Inject(method = "isBreedingItem", at = @At("HEAD"), cancellable = true)
	public void isBreedingItem(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		cir.setReturnValue(stack.isIn(ItemTags.PARROT_FOOD));
	}

	@Inject(method = "createChild", at = @At("HEAD"), cancellable = true)
	public void createChild(ServerWorld world, PassiveEntity entity, CallbackInfoReturnable<PassiveEntity> cir) {
		var baby = EntityType.PARROT.create(world);
		if (baby != null && entity instanceof ParrotEntity otherParrot) {
			if (this.getRandom().nextBoolean()) {
				baby.setVariant(this.getVariant());
			} else {
				baby.setVariant(otherParrot.getVariant());
			}
			if (this.isTamed()) {
				baby.setOwnerUuid(this.getOwnerUuid());
				baby.setTamed(true, true);
			}
		}
		cir.setReturnValue(baby);
	}

	@Unique
	private boolean wasFed = false;

	@Unique
	private void markFed(Hand hand) {
		if (hand == Hand.MAIN_HAND) {
			wasFed = true;
		}
	}

	@Inject(
		method = "interactMob",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/entity/passive/ParrotEntity;setSitting(Z)V"
		),
		cancellable = true
	)
	public void interactWhenTamed(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
		if (wasFed && hand == Hand.OFF_HAND) {
			cir.setReturnValue(ActionResult.PASS);
		}
		if (player.getStackInHand(hand).isIn(ItemTags.PARROT_FOOD) && this.getHealth() < this.getMaxHealth()) {
			var stack = player.getStackInHand(hand);
			this.eat(player, hand, stack);
			FoodComponent foodComponent = stack.get(DataComponentTypes.FOOD);
			this.heal(foodComponent != null ? foodComponent.nutrition() : 1.0f);
			markFed(hand);
			cir.setReturnValue(ActionResult.SUCCESS);
		} else {
			var result = super.interactMob(player, hand);
			if (result.isAccepted()) {
				cir.setReturnValue(result);
				markFed(hand);
			}
		}
	}

	@Inject(method = "initGoals", at = @At("HEAD"))
	public void initGoals(CallbackInfo ci) {
		this.goalSelector.add(3, new AnimalMateGoal(this, 0.8));
	}
}
