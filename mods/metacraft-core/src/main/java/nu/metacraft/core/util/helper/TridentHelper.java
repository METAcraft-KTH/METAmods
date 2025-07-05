package nu.metacraft.core.util.helper;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.TridentItem;
import nu.metacraft.core.entity.TridentUser;

import java.util.Optional;

public class TridentHelper {

	public static Optional<Boolean> shouldThrowTrident(MobEntity mob, LivingEntity target) {
		return HandHelper.checkEachHand(mob, stack -> stack.getItem() instanceof TridentItem).map(
				hand -> {
					var trident = mob.getStackInHand(hand);
					float spinAttackStrength = EnchantmentHelper.getTridentSpinAttackStrength(trident, mob);
					if (spinAttackStrength <= 0) {
						return !mob.isInAttackRange(target);
					} else {
						return mob instanceof TridentUser t && t.canUseRiptide(trident);
					}
				}
		);
	}

	public static int getTridentRange() {
		return 10;
	}

}
