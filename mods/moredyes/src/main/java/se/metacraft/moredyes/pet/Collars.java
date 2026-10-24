package se.metacraft.moredyes.pet;

import se.metacraft.moredyes.content.item.ModDyeItem;
import se.metacraft.moredyes.mixin.CatAccessor;
import se.metacraft.moredyes.mixin.WolfAccessor;
import se.metacraft.moredyes.sign.SignColors;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;

/**
 * Our dye on a wolf's or cat's collar. The collar is a {@link DyeColor} that the client tints by, so
 * it cannot be our colour; it becomes the nearest vanilla one. Like vanilla, only the owner can, and
 * a dye the collar already has is not used up.
 */
public final class Collars {
	private Collars() {}

	public static void init() {
		// Our dye is not a DyeItem, so the pet's own mobInteract would sit it down instead.
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide()) return InteractionResult.PASS;
			ItemStack stack = player.getItemInHand(hand);
			if (!(stack.getItem() instanceof ModDyeItem dye)) return InteractionResult.PASS;
			DyeColor collar = SignColors.nearestDye(dye.color());
			if (entity instanceof Wolf wolf && wolf.isTame() && wolf.isOwnedBy(player)) {
				if (wolf.getCollarColor() == collar) return InteractionResult.PASS;
				((WolfAccessor) wolf).moredyes$setCollarColor(collar);
			} else if (entity instanceof Cat cat && cat.isTame() && cat.isOwnedBy(player)) {
				if (cat.getCollarColor() == collar) return InteractionResult.PASS;
				((CatAccessor) cat).moredyes$setCollarColor(collar);
			} else {
				return InteractionResult.PASS;
			}
			level.playSound(null, entity, SoundEvents.DYE_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
			if (!player.getAbilities().instabuild) stack.shrink(1);
			return InteractionResult.SUCCESS;
		});
	}
}
