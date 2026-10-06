package nu.metacraft.core.block.blocks;

import eu.pb4.polymer.core.api.block.PolymerBlock;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.BlockHitResult;
import nu.metacraft.core.block.entities.PortalEntity;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

public class PortalCore extends BaseEntityBlock implements PolymerBlock, Portal, PortalEntity.ConnectedToPortalBlockEntity {


	public PortalCore(Properties settings) {
		super(settings);
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new PortalEntity(pos, state);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		return getPortal(world, pos).map(
			portal ->  portal.interactWithItem(stack, state, world, pos, player, hand, hit)
		).orElseGet(() -> super.useItemOn(stack, state, world, pos, player, hand, hit));
	}

	@Override
	public void onPolymerBlockSend(BlockState blockState, BlockPos.MutableBlockPos pos, ServerPlayer player) {
		PortalPadding.sendDummyEndGateway(pos, player);
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext ctx) {
		return Blocks.END_GATEWAY.defaultBlockState();
	}

	@Override
	protected void entityInside(
		final @NonNull BlockState state, final @NonNull Level level, final @NonNull BlockPos pos,
		final @NonNull Entity entity, final @NonNull InsideBlockEffectApplier effectApplier, final boolean isPrecise
	) {
		getPortal(level, pos).ifPresent(
			portal -> portal.entityInside(state, this, level, pos, entity, effectApplier, isPrecise)
		);
	}

	@Override
	public @Nullable TeleportTransition getPortalDestination(
		@NonNull ServerLevel currentLevel, @NonNull Entity entity, @NonNull BlockPos portalEntryPos
	) {
		return getPortal(currentLevel, portalEntryPos).map(
			p -> p.getPortalDestination(currentLevel, entity, portalEntryPos)
		).orElse(null);
	}

	@Override
	public Optional<PortalEntity> getPortal(Level level, BlockPos blockPos) {
		if (level.getBlockEntity(blockPos) instanceof PortalEntity portal) {
			return Optional.of(portal);
		}
		return Optional.empty();
	}
}
