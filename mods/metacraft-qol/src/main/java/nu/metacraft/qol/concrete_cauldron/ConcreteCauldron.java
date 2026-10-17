package nu.metacraft.qol.concrete_cauldron;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ConcretePowderBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import nu.metacraft.qol.mixin.ConcretePowderBlockAccessor;

/**
 * Concrete powder thrown into a water cauldron: the whole dropped stack turns into the matching
 * concrete, and the cauldron loses one level. One level per stack, however big, so throwing full
 * stacks is the cheap way.
 */
public final class ConcreteCauldron {

	private ConcreteCauldron() {}

	/** Called for every entity touching a layered cauldron's contents. */
	public static void onEntityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
		if (!(level instanceof ServerLevel serverLevel) || !state.is(Blocks.WATER_CAULDRON)) {
			return;
		}
		if (!(entity instanceof ItemEntity item) || item.isRemoved() || !ConcreteCauldronConfig.getInstance().enabled()) {
			return;
		}
		var stack = item.getItem();
		if (!(stack.getItem() instanceof BlockItem blockItem) || !(blockItem.getBlock() instanceof ConcretePowderBlock powder)) {
			return;
		}
		item.setItem(stack.transmuteCopy(((ConcretePowderBlockAccessor) powder).getConcrete()));
		LayeredCauldronBlock.lowerFillLevel(state, level, pos);
		level.playSound(null, pos, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 0.5f, 1.3f);
		serverLevel.sendParticles(
				ParticleTypes.SPLASH, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 12, 0.2, 0.05, 0.2, 0.1
		);
	}

}
