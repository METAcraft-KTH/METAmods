package se.datasektionen.mc.better_pets.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import se.datasektionen.mc.better_pets.TameableExtension;

import java.util.Optional;

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

	@ModifyExpressionValue(
		method = "interactMob",
		at = @At(
			value = "FIELD",
			target = "Lnet/minecraft/util/ActionResult;SUCCESS:Lnet/minecraft/util/ActionResult$Success;"
		)
	)
	public ActionResult.Success swingArm(ActionResult.Success original, @Share("notOwner") LocalBooleanRef notOwner) {
		if (notOwner.get()) {
			return ActionResult.SUCCESS_SERVER;
		}
		return original;
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
		var baby = EntityType.PARROT.create(world, SpawnReason.BREEDING);
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
	private Optional<ActionResult> interactParrot(PlayerEntity player, Hand hand) {
		if (hand == Hand.OFF_HAND) {
			return Optional.of(ActionResult.FAIL);
		}
		if (player.getStackInHand(hand).isIn(ItemTags.PARROT_FOOD) && this.getHealth() < this.getMaxHealth()) {
			var stack = player.getStackInHand(hand);
			this.eat(player, hand, stack);
			FoodComponent foodComponent = stack.get(DataComponentTypes.FOOD);
			this.heal(foodComponent != null ? foodComponent.nutrition() : 1.0f);
			return Optional.of(ActionResult.SUCCESS_SERVER);
		} else {
			var result = super.interactMob(player, hand);
			if (result.isAccepted()) {
				return Optional.of(result);
			}
		}
		return Optional.empty();
	}

	@Inject(
		method = "interactMob",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/entity/passive/ParrotEntity;isInAir()Z"
		),
		cancellable = true
	)
	public void interactWhenTamed(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
		if (!player.getWorld().isClient() && isTamed() && (isOwner(player) || ((TameableExtension) this).metaraft$isTrusted(player))) {
			interactParrot(player, hand).ifPresent(cir::setReturnValue);
		}
	}

	@Inject(method = "initGoals", at = @At("HEAD"))
	public void initGoals(CallbackInfo ci) {
		this.goalSelector.add(3, new AnimalMateGoal(this, 0.8));
	}
}
