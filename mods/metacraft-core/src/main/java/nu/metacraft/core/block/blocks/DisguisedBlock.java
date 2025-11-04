package nu.metacraft.core.block.blocks;

import eu.pb4.polymer.core.api.block.PolymerBlock;
import nu.metacraft.core.block.entities.BlockEntityWithDisguise;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public abstract class DisguisedBlock extends BaseEntityBlock implements PolymerBlock, BlockWithDisguise {

	public DisguisedBlock(Properties settings) {
		super(settings);
	}

	@Override
	protected float getDestroyProgress(BlockState state, Player player, BlockGetter world, BlockPos pos) {
		float hardness = state.getDestroySpeed(world, pos); //Handled by mixin.
		if (hardness == -1.0f) {
			return 0.0f;
		}
		var disguised = getBlockEntity(world, pos);
		if (disguised.isPresent()) {
			state = disguised.get().getBlockState();
		}
		int toolModifier = player.hasCorrectToolForDrops(state) ? 30 : 100;
		return player.getDestroySpeed(state) / hardness / toolModifier;
	}

	@Override
	public void onPolymerBlockSend(BlockState blockState, BlockPos.MutableBlockPos pos, PacketContext.NotNullWithPlayer context) {
		getBlockEntity(context.getPlayer().level(), pos).ifPresent(disguised -> disguised.updateClient(context.getPlayer()));
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state, PacketContext ctx) {
		return Blocks.BARRIER.defaultBlockState();
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
		return getBlockEntity(world, pos).map(disguised -> disguised.getBlockState().getShape(world, pos, context)).orElse(
				super.getShape(state, world, pos, context)
		);
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
		return getBlockEntity(world, pos).map(disguised -> disguised.getBlockState().getCollisionShape(world, pos, context)).orElse(
				super.getCollisionShape(state, world, pos, context)
		);
	}

	@Override
	protected List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
		Object blockEntity = builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
		if (blockEntity == null) {
			var pos = builder.getOptionalParameter(LootContextParams.ORIGIN);
			if (pos != null) {
				blockEntity = getBlockEntity(builder.getLevel(), BlockPos.containing(pos)).orElse(null);
			}
		}
		return Optional.ofNullable(blockEntity).filter(
				entity -> entity instanceof BlockEntityWithDisguise
		).map(
				entity -> (BlockEntityWithDisguise) entity
		).map(BlockEntityWithDisguise::getBlockState).filter(
				s -> !(s.getBlock() instanceof DisguisedBlock)
		).map(
				s -> s.getDrops(builder)
		).orElse(
				super.getDrops(state, builder)
		);
	}
}
