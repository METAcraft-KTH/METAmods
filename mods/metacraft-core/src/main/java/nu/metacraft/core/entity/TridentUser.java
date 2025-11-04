package nu.metacraft.core.entity;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.core.util.helper.EntityAIHelper;

public interface TridentUser extends RangedAttackMob {

	default boolean canUseRiptide(ItemStack stack) {
		return ((Entity) this).isInWaterOrRain();
	}

	default boolean throwTrident(ItemStack stack, LivingEntity target, float speed, float divergence, boolean infiniteAmmo) {
		LivingEntity thrower = (LivingEntity) this;
		float spinAttackStrength = EnchantmentHelper.getTridentSpinAttackStrength(stack, thrower);
		var sound = EnchantmentHelper.pickHighestLevel(
				stack, EnchantmentEffectComponents.TRIDENT_SOUND
		).orElse(SoundEvents.TRIDENT_THROW).value();
		if (spinAttackStrength <= 0) {
			ThrownTrident tridentEntity = new ThrownTrident(thrower.level(), thrower, stack);
			EntityAIHelper.shootProjectile(
					thrower, tridentEntity, target, sound, speed, divergence
			);
			if (!infiniteAmmo) {
				stack.shrink(1);
			}
		} else if (this.canUseRiptide(stack)) {
			thrower.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
			float yaw = thrower.getYRot();
			float pitch = thrower.getXRot();
			float j = -Mth.sin(yaw * ((float)Math.PI / 180)) * Mth.cos(pitch * ((float)Math.PI / 180));
			float k = -Mth.sin(pitch * ((float)Math.PI / 180));
			float l = Mth.cos(yaw * ((float)Math.PI / 180)) * Mth.cos(pitch * ((float)Math.PI / 180));
			float m = Mth.sqrt(j * j + k * k + l * l);
			thrower.push(j * spinAttackStrength / m, k * spinAttackStrength / m, l * spinAttackStrength / m);
			this.activateRiptide(20, 8.0f, stack);
			if (thrower.onGround()) {
				thrower.move(MoverType.SELF, new Vec3(0.0, 1.1999999f, 0.0));
			}
			thrower.level().playSound(null, thrower, sound, SoundSource.PLAYERS, 1.0f, 1.0f);
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
