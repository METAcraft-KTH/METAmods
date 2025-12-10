package nu.metacraft.core.util.helper;

import nu.metacraft.core.entity.TridentUser;

import java.util.Optional;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

public class TridentHelper {

	public static Optional<Boolean> shouldThrowTrident(Mob mob, LivingEntity target) {
		return HandHelper.checkEachHand(mob, stack -> stack.getItem() instanceof TridentItem).map(
				hand -> {
					var trident = mob.getItemInHand(hand);
					float spinAttackStrength = EnchantmentHelper.getTridentSpinAttackStrength(trident, mob);
					if (spinAttackStrength <= 0) {
						return !mob.isWithinMeleeAttackRange(target);
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
