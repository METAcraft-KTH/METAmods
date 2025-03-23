package se.datasektionen.mc.metacraft_core.block.blocks;

import com.mojang.serialization.MapCodec;
import eu.pb4.polymer.core.api.block.PolymerBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCollisionHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import se.datasektionen.mc.metacraft_core.block.entities.PortalEntity;
import xyz.nucleoid.packettweaker.PacketContext;

public class PortalCore extends BlockWithEntity implements PolymerBlock {

	public static final MapCodec<PortalCore> CODEC = createCodec(PortalCore::new);

	public PortalCore(Settings settings) {
		super(settings);
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new PortalEntity(pos, state);
	}

	@Override
	public void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity, EntityCollisionHandler handler) {
		if (world.getBlockEntity(pos) instanceof PortalEntity portal) {
			portal.onCollision(state, world, pos, entity);
		}
	}

	@Override
	protected ActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		if (world.getBlockEntity(pos) instanceof PortalEntity portal) {
			return portal.interactWithItem(stack, state, world, pos, player, hand, hit);
		}
		return super.onUseWithItem(stack, state, world, pos, player, hand, hit);
	}

	@Override
	public void onPolymerBlockSend(BlockState blockState, BlockPos.Mutable pos, PacketContext.NotNullWithPlayer ctx) {
		PortalPadding.sendDummyEndGateway(pos, ctx);
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state, PacketContext ctx) {
		return Blocks.END_GATEWAY.getDefaultState();
	}
}
