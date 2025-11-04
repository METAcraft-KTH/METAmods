package nu.metacraft.better_pets.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import nu.metacraft.better_pets.TameableExtension;

import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

@Mixin(Parrot.class)
public abstract class ParrotMixin extends TamableAnimal {

	@Shadow public abstract Parrot.Variant getVariant();

	protected ParrotMixin(EntityType<? extends TamableAnimal> entityType, Level world) {
		super(entityType, world);
	}

	@ModifyExpressionValue(
			method = "mobInteract",
			at = @At(
					value = "INVOKE", target = "Lnet/minecraft/world/entity/animal/Parrot;isOwnedBy(Lnet/minecraft/world/entity/LivingEntity;)Z"
			)
	)
	public boolean isTrusted(
			boolean original, @Local(argsOnly = true) Player player, @Local(argsOnly = true) InteractionHand hand, @Share("notOwner") LocalBooleanRef notOwner
	) {
		if (original) {
			return true;
		} else if (hand == InteractionHand.MAIN_HAND) { //Check for main hand to prevent double interactions.
			notOwner.set(((TameableExtension) this).metaraft$isTrusted(player));
			return notOwner.get();
		}
		return false;
	}

	@ModifyExpressionValue(
		method = "mobInteract",
		at = @At(
			value = "FIELD",
			target = "Lnet/minecraft/world/InteractionResult;SUCCESS:Lnet/minecraft/world/InteractionResult$Success;"
		)
	)
	public InteractionResult.Success swingArm(InteractionResult.Success original, @Share("notOwner") LocalBooleanRef notOwner) {
		if (notOwner.get()) {
			return InteractionResult.SUCCESS_SERVER;
		}
		return original;
	}

	@ModifyReturnValue(method = "isBaby", at = @At("RETURN"))
	public boolean isBaby(boolean original) {
		return super.isBaby();
	}

	@Inject(method = "canMate", at = @At("HEAD"), cancellable = true)
	public void canBreedWith(Animal other, CallbackInfoReturnable<Boolean> cir) {
		cir.setReturnValue(super.canMate(other));
	}

	@Inject(method = "isFood", at = @At("HEAD"), cancellable = true)
	public void isBreedingItem(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		cir.setReturnValue(stack.is(ItemTags.PARROT_FOOD));
	}

	@Inject(method = "getBreedOffspring", at = @At("HEAD"), cancellable = true)
	public void createChild(ServerLevel world, AgeableMob entity, CallbackInfoReturnable<AgeableMob> cir) {
		var baby = EntityType.PARROT.create(world, EntitySpawnReason.BREEDING);
		if (baby != null && entity instanceof Parrot otherParrot) {
			if (this.getRandom().nextBoolean()) {
				baby.setComponent(DataComponents.PARROT_VARIANT, this.getVariant());
			} else {
				baby.setComponent(DataComponents.PARROT_VARIANT, otherParrot.getVariant());
			}
			if (this.isTame()) {
				baby.setOwnerReference(this.getOwnerReference());
				baby.setTame(true, true);
			}
		}
		cir.setReturnValue(baby);
	}

	@Unique
	private Optional<InteractionResult> interactParrot(Player player, InteractionHand hand) {
		if (hand == InteractionHand.OFF_HAND) {
			return Optional.of(InteractionResult.FAIL);
		}
		if (player.getItemInHand(hand).is(ItemTags.PARROT_FOOD) && this.getHealth() < this.getMaxHealth()) {
			var stack = player.getItemInHand(hand);
			this.usePlayerItem(player, hand, stack);
			FoodProperties foodComponent = stack.get(DataComponents.FOOD);
			this.heal(foodComponent != null ? foodComponent.nutrition() : 1.0f);
			return Optional.of(InteractionResult.SUCCESS_SERVER);
		} else {
			var result = super.mobInteract(player, hand);
			if (result.consumesAction()) {
				return Optional.of(result);
			}
		}
		return Optional.empty();
	}

	@Inject(
		method = "mobInteract",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/animal/Parrot;isFlying()Z"
		),
		cancellable = true
	)
	public void interactWhenTamed(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		if (!player.level().isClientSide() && isTame() && (isOwnedBy(player) || ((TameableExtension) this).metaraft$isTrusted(player))) {
			interactParrot(player, hand).ifPresent(cir::setReturnValue);
		}
	}

	@Inject(method = "registerGoals", at = @At("HEAD"))
	public void initGoals(CallbackInfo ci) {
		this.goalSelector.addGoal(3, new BreedGoal(this, 0.8));
	}
}
