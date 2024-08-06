package se.datasektionen.mc.metacraft_core.block.blocks;

import com.mojang.serialization.MapCodec;
import eu.pb4.polymer.core.api.block.PolymerBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import se.datasektionen.mc.metacraft_core.block.entities.TrapSpawnerEntity;

import java.util.Optional;

public class TrapSpawner extends BlockWithEntity implements PolymerBlock {

	public static final MapCodec<TrapSpawner> CODEC = TrapSpawner.createCodec(TrapSpawner::new);

	public TrapSpawner(Settings settings) {
		super(settings);
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		return getTrap(world, pos).map(
				trap -> {
					trap.triggerInteract(hit.getSide(), player);
					return ActionResult.SUCCESS;
				}
		).orElse(super.onUse(state, world, pos, player, hit));
	}

	@Override
	protected void onBlockBreakStart(BlockState state, World world, BlockPos pos, PlayerEntity player) {
		super.onBlockBreakStart(state, world, pos, player);
		var hit = player.raycast(player.getAttributeValue(EntityAttributes.PLAYER_BLOCK_INTERACTION_RANGE), 0, false);
		if (hit != null && hit.getType() != HitResult.Type.MISS) {
			getTrap(world, pos).ifPresent(trap -> trap.triggerInteract(((BlockHitResult) hit).getSide(), player));
		}
	}

	@Override
	public void onSteppedOn(World world, BlockPos pos, BlockState state, Entity entity) {
		super.onSteppedOn(world, pos, state, entity);
		getTrap(world, pos).ifPresent(trap -> trap.triggerStep(entity));
	}

	@Override
	protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.isOf(newState.getBlock()) && !moved) {
			getTrap(world, pos).ifPresent(TrapSpawnerEntity::triggerRemove);
		}
		super.onStateReplaced(state, world, pos, newState, moved);
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new TrapSpawnerEntity(pos, state);
	}

	@Override
	public void onPolymerBlockSend(BlockState blockState, BlockPos.Mutable pos, ServerPlayerEntity player) {
		getTrap(player.getWorld(), pos).ifPresent(trap -> trap.updateClient(player));
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state) {
		return Blocks.BARRIER.getDefaultState();
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return getTrap(world, pos).map(trap -> trap.getBlockState().getOutlineShape(world, pos, context)).orElse(
				super.getOutlineShape(state, world, pos, context)
		);
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return getTrap(world, pos).map(trap -> trap.getBlockState().getCollisionShape(world, pos, context)).orElse(
				super.getCollisionShape(state, world, pos, context)
		);
	}

	private Optional<TrapSpawnerEntity> getTrap(BlockView world, BlockPos pos) {
		var entity = world.getBlockEntity(pos);
		if (entity instanceof TrapSpawnerEntity trap) {
			return Optional.of(trap);
		}
		return Optional.empty();
	}
}
