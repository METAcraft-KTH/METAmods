package nu.metacraft.core.util.helper;

import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

import java.util.Optional;
import java.util.function.Predicate;

public class HandHelper {

	public static Optional<Hand> checkEachHand(LivingEntity player, Predicate<ItemStack> stack) {
		if (stack.test(player.getMainHandStack())) {
			return Optional.of(Hand.MAIN_HAND);
		} else if (stack.test(player.getOffHandStack())) {
			return Optional.of(Hand.OFF_HAND);
		} else {
			return Optional.empty();
		}
	}

}
