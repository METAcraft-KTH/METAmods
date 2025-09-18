package nu.metacraft.core.entity;

import net.minecraft.command.argument.EntityAnchorArgumentType;
import net.minecraft.component.EnchantmentEffectComponentTypes;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.ai.RangedAttackMob;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import nu.metacraft.core.util.helper.EntityAIHelper;

public interface TridentUser extends RangedAttackMob {

	default boolean canUseRiptide(ItemStack stack) {
		return ((Entity) this).isTouchingWaterOrRain();
	}

	default boolean throwTrident(ItemStack stack, LivingEntity target, float speed, float divergence, boolean infiniteAmmo) {
		LivingEntity thrower = (LivingEntity) this;
		float spinAttackStrength = EnchantmentHelper.getTridentSpinAttackStrength(stack, thrower);
		var sound = EnchantmentHelper.getEffect(
				stack, EnchantmentEffectComponentTypes.TRIDENT_SOUND
		).orElse(SoundEvents.ITEM_TRIDENT_THROW).value();
		if (spinAttackStrength <= 0) {
			TridentEntity tridentEntity = new TridentEntity(thrower.getEntityWorld(), thrower, stack);
			EntityAIHelper.shootProjectile(
					thrower, tridentEntity, target, sound, speed, divergence
			);
			if (!infiniteAmmo) {
				stack.decrement(1);
			}
		} else if (this.canUseRiptide(stack)) {
			thrower.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, target.getEyePos());
			float yaw = thrower.getYaw();
			float pitch = thrower.getPitch();
			float j = -MathHelper.sin(yaw * ((float)Math.PI / 180)) * MathHelper.cos(pitch * ((float)Math.PI / 180));
			float k = -MathHelper.sin(pitch * ((float)Math.PI / 180));
			float l = MathHelper.cos(yaw * ((float)Math.PI / 180)) * MathHelper.cos(pitch * ((float)Math.PI / 180));
			float m = MathHelper.sqrt(j * j + k * k + l * l);
			thrower.addVelocity(j * spinAttackStrength / m, k * spinAttackStrength / m, l * spinAttackStrength / m);
			this.activateRiptide(20, 8.0f, stack);
			if (thrower.isOnGround()) {
				thrower.move(MovementType.SELF, new Vec3d(0.0, 1.1999999f, 0.0));
			}
			thrower.getEntityWorld().playSoundFromEntity(null, thrower, sound, SoundCategory.PLAYERS, 1.0f, 1.0f);
		} else {
			return false;
		}
		return true;
	}

	/**
	 * Override this, set the 3 parameters in the entity data
	 * and set {@link LivingEntity#USING_RIPTIDE_FLAG} to true.
	 * @param riptideTicks The time to keep riptide active, put this in {@link LivingEntity#riptideTicks}.
	 * @param riptideAttackDamage The damage riptide should deal, put this in {@link LivingEntity#riptideAttackDamage}.
	 * @param riptideStack The item stack, put this in {@link LivingEntity#riptideStack}.
	 */
	void activateRiptide(int riptideTicks, float riptideAttackDamage, ItemStack riptideStack);

}
