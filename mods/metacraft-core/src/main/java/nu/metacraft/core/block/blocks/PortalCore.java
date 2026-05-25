package nu.metacraft.core.block.blocks;

import com.mojang.serialization.MapCodec;
import eu.pb4.polymer.core.api.block.PolymerBlock;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import nu.metacraft.core.block.entities.PortalEntity;

public class PortalCore extends BaseEntityBlock implements PolymerBlock {

	public static final MapCodec<PortalCore> CODEC = simpleCodec(PortalCore::new);

	public PortalCore(Properties settings) {
		super(settings);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new PortalEntity(pos, state);
	}

	@Override
	public void entityInside(BlockState state, Level world, BlockPos pos, Entity entity, InsideBlockEffectApplier handler, boolean bl) {
		if (world.getBlockEntity(pos) instanceof PortalEntity portal) {
			portal.onCollision(state, world, pos, entity);
		}
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		if (world.getBlockEntity(pos) instanceof PortalEntity portal) {
			return portal.interactWithItem(stack, state, world, pos, player, hand, hit);
		}
		return super.useItemOn(stack, state, world, pos, player, hand, hit);
	}

	@Override
	public void onPolymerBlockSend(BlockState blockState, BlockPos.MutableBlockPos pos, ServerPlayer player) {
		PortalPadding.sendDummyEndGateway(pos, player);
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext ctx) {
		return Blocks.END_GATEWAY.defaultBlockState();
	}
}
