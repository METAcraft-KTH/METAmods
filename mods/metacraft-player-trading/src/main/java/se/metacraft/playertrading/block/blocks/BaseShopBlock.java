package se.metacraft.playertrading.block.blocks;

import eu.pb4.polymer.core.api.block.PolymerHeadBlock;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import se.metacraft.playertrading.block.entities.ShopBlockEntity;

public abstract class BaseShopBlock extends BaseEntityBlock implements PolymerHeadBlock {

	public static final String SKIN = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvN2UzZGViNTdlYWEyZjRkNDAzYWQ1NzI4M2NlOGI0MTgwNWVlNWI2ZGU5MTJlZTJiNGVhNzM2YTlkMWY0NjVhNyJ9fX0=";

	public BaseShopBlock(Properties properties) {
		super(properties);
	}

	@Override
	public String getPolymerSkinValue(BlockState state, BlockPos pos, PacketContext context) {
		return SKIN;
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(@NonNull BlockPos worldPosition, @NonNull BlockState blockState) {
		return new ShopBlockEntity(worldPosition, blockState);
	}

	@Override
	protected @NonNull BlockState updateShape(
		final @NonNull BlockState state,
		final @NonNull LevelReader level,
		final @NonNull ScheduledTickAccess ticks,
		final @NonNull BlockPos pos,
		final @NonNull Direction directionToNeighbour,
		final @NonNull BlockPos neighbourPos,
		final @NonNull BlockState neighbourState,
		final @NonNull RandomSource random
	) {
		if (getContainerPos(state, level, pos).equals(neighbourPos) && !state.canSurvive(level, pos)) {
			return Blocks.AIR.defaultBlockState();
		}
		return state;
	}

	protected @NonNull InteractionResult useItemOn(
		final @NonNull ItemStack itemStack,
		final @NonNull BlockState state,
		final @NonNull Level level,
		final @NonNull BlockPos pos,
		final @NonNull Player player,
		final @NonNull InteractionHand hand,
		final @NonNull BlockHitResult hitResult
	) {
		if (level.getBlockEntity(pos) instanceof ShopBlockEntity shop) {
			return shop.onUse(player, pos, hand);
		}
		return super.useItemOn(itemStack, state, level, pos, player, hand, hitResult);
	}


	@Override
	protected @NonNull InteractionResult useWithoutItem(
		final @NonNull BlockState state, final @NonNull Level level, final @NonNull BlockPos pos,
		final @NonNull Player player, final @NonNull BlockHitResult hitResult
	) {
		if (level.getBlockEntity(pos) instanceof ShopBlockEntity shop) {
			return shop.onUse(player, pos);
		}
		return super.useWithoutItem(state, level, pos, player, hitResult);
	}

	public abstract Direction getContainerDirection(BlockState state, LevelReader level, BlockPos pos);

	public BlockPos getContainerPos(BlockState state, LevelReader level, BlockPos pos) {
		return pos.relative(getContainerDirection(state, level, pos));
	}

	public static boolean validShopContainer(LevelReader level, BlockPos pos) {
		return level.getBlockState(pos).is(Blocks.BARREL);
	}

	public static void updateShopStash(Level level, BlockPos pos) {
		ShopBlockEntity.getConnectedTo(level, pos).ifPresent(ShopBlockEntity::triggerUpdate);
		if (level.getBlockEntity(pos) instanceof HopperBlockEntity hopper) {
			ShopBlockEntity.getConnectedTo(level, pos.above()).ifPresent(ShopBlockEntity::triggerUpdate);
			ShopBlockEntity.getConnectedTo(
					level, pos.relative(hopper.getBlockState().getValue(HopperBlock.FACING))
			).ifPresent(ShopBlockEntity::triggerUpdate);
		}
	}

	@Override
	protected boolean canSurvive(
		final @NonNull BlockState state, final @NonNull LevelReader level, final @NonNull BlockPos pos
	) {
		var containerPos = getContainerPos(state, level, pos);
		if (level instanceof Level l) {
			var otherPos = ShopBlockEntity.getConnectedTo(l, containerPos).map(BlockEntity::getBlockPos);
			if (otherPos.isPresent() && !otherPos.get().equals(pos)) return false;
		}
		return validShopContainer(level, containerPos);
	}

	@Override
	public void setPlacedBy(
		final @NonNull Level level, final @NonNull BlockPos pos, final @NonNull BlockState state,
		final @Nullable LivingEntity by, final @NonNull ItemStack itemStack
	) {
		super.setPlacedBy(level, pos, state, by, itemStack);
		if (level.getBlockEntity(pos) instanceof ShopBlockEntity shop) {
			shop.setPlacedBy(by);
		}
	}

}
