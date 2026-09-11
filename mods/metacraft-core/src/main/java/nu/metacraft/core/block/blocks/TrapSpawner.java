package nu.metacraft.core.block.blocks;

import org.jetbrains.annotations.Nullable;
import nu.metacraft.core.block.entities.TrapSpawnerEntity;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class TrapSpawner extends DisguisedBlock {

	public TrapSpawner(Properties settings) {
		super(settings);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
		return getTrap(world, pos).map(
				trap -> {
					return trap.triggerInteract(hit.getDirection(), player);
				}
		).orElse(super.useWithoutItem(state, world, pos, player, hit));
	}

	@Override
	protected void attack(BlockState state, Level world, BlockPos pos, Player player) {
		super.attack(state, world, pos, player);
		var hit = player.pick(player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE), 0, false);
		if (hit != null && hit.getType() != HitResult.Type.MISS) {
			getTrap(world, pos).ifPresent(trap -> trap.triggerInteract(((BlockHitResult) hit).getDirection(), player));
		}
	}

	@Override
	public void stepOn(Level world, BlockPos pos, BlockState state, Entity entity) {
		super.stepOn(world, pos, state, entity);
		getTrap(world, pos).ifPresent(trap -> trap.triggerStep(entity));
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel world, BlockPos pos, boolean moved) {
		if (!state.is(world.getBlockState(pos).getBlock()) && !moved) {
			getTrap(world, pos).ifPresent(TrapSpawnerEntity::triggerRemove);
		}
		super.affectNeighborsAfterRemoval(state, world, pos, moved);
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TrapSpawnerEntity(pos, state);
	}

	public Optional<TrapSpawnerEntity> getTrap(BlockGetter world, BlockPos pos) {
		var entity = world.getBlockEntity(pos);
		if (entity instanceof TrapSpawnerEntity trap) {
			return Optional.of(trap);
		}
		return Optional.empty();
	}
}
