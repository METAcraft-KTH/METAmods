package se.datasektionen.mc.metacraft_core.block.blocks;

import eu.pb4.polymer.core.api.block.PolymerBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import se.datasektionen.mc.metacraft_core.block.entities.BlockEntityWithDisguise;

import java.util.List;
import java.util.Optional;

public abstract class DisguisedBlock extends BlockWithEntity implements PolymerBlock, BlockWithDisguise {

	public DisguisedBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected float calcBlockBreakingDelta(BlockState state, PlayerEntity player, BlockView world, BlockPos pos) {
		float hardness = state.getHardness(world, pos); //Handled by mixin.
		if (hardness == -1.0f) {
			return 0.0f;
		}
		var disguised = getBlockEntity(world, pos);
		if (disguised.isPresent()) {
			state = disguised.get().getBlockState();
		}
		int toolModifier = player.canHarvest(state) ? 30 : 100;
		return player.getBlockBreakingSpeed(state) / hardness / toolModifier;
	}

	@Override
	public void onPolymerBlockSend(BlockState blockState, BlockPos.Mutable pos, ServerPlayerEntity player) {
		getBlockEntity(player.getWorld(), pos).ifPresent(disguised -> disguised.updateClient(player));
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state) {
		return Blocks.BARRIER.getDefaultState();
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return getBlockEntity(world, pos).map(disguised -> disguised.getBlockState().getOutlineShape(world, pos, context)).orElse(
				super.getOutlineShape(state, world, pos, context)
		);
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return getBlockEntity(world, pos).map(disguised -> disguised.getBlockState().getCollisionShape(world, pos, context)).orElse(
				super.getCollisionShape(state, world, pos, context)
		);
	}

	@Override
	protected List<ItemStack> getDroppedStacks(BlockState state, LootContextParameterSet.Builder builder) {
		Object blockEntity = builder.getOptional(LootContextParameters.BLOCK_ENTITY);
		if (blockEntity == null) {
			var pos = builder.getOptional(LootContextParameters.ORIGIN);
			if (pos != null) {
				blockEntity = getBlockEntity(builder.getWorld(), BlockPos.ofFloored(pos)).orElse(null);
			}
		}
		return Optional.ofNullable(blockEntity).filter(
				entity -> entity instanceof BlockEntityWithDisguise
		).map(
				entity -> (BlockEntityWithDisguise) entity
		).map(BlockEntityWithDisguise::getBlockState).filter(
				s -> !(s.getBlock() instanceof DisguisedBlock)
		).map(
				s -> s.getDroppedStacks(builder)
		).orElse(
				super.getDroppedStacks(state, builder)
		);
	}
}
