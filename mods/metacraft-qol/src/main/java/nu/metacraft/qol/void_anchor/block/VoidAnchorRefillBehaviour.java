package nu.metacraft.qol.void_anchor.block;

import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.OptionalDispenseItemBehavior;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;

/** A dispenser holding the fuel item tops up the void anchor in front of it, as one with glowstone does a respawn anchor. */
public class VoidAnchorRefillBehaviour extends OptionalDispenseItemBehavior {

	@Override
	protected ItemStack execute(BlockSource source, ItemStack stack) {
		var level = source.level();
		var pos = source.pos().relative(source.state().getValue(DispenserBlock.FACING));
		var state = level.getBlockState(pos);
		if (state.getBlock() instanceof VoidAnchorBlock && VoidAnchorBlock.canCharge(state)) {
			RespawnAnchorBlock.charge(null, level, pos, state);
			stack.shrink(1);
			setSuccess(true);
		} else {
			setSuccess(false);
		}
		return stack;
	}

}
