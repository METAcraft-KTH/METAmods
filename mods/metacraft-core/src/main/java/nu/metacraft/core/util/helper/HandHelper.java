package nu.metacraft.core.util.helper;

import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public class HandHelper {

	public static Optional<InteractionHand> checkEachHand(LivingEntity player, Predicate<ItemStack> stack) {
		if (stack.test(player.getMainHandItem())) {
			return Optional.of(InteractionHand.MAIN_HAND);
		} else if (stack.test(player.getOffhandItem())) {
			return Optional.of(InteractionHand.OFF_HAND);
		} else {
			return Optional.empty();
		}
	}

}
