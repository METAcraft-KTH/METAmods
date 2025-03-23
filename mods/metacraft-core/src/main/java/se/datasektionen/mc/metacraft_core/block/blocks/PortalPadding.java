package se.datasektionen.mc.metacraft_core.block.blocks;

import eu.pb4.polymer.core.api.block.PolymerBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.EndGatewayBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCollisionHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import se.datasektionen.mc.metacraft_core.block.entities.PortalEntity;
import xyz.nucleoid.packettweaker.PacketContext;

public class PortalPadding extends Block implements PolymerBlock {

	public PortalPadding(Settings settings) {
		super(settings);
	}

	@Override
	public void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity, EntityCollisionHandler handler) {
		for (BlockPos portalPos : PortalEntity.forAllNearbyPortals(world, pos)) {
			if (world.getBlockEntity(portalPos) instanceof PortalEntity portal) {
				portal.onCollision(state, world, pos, entity);
			}
		}
	}

	@Override
	protected ActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		for (BlockPos portalPos : PortalEntity.forAllNearbyPortals(world, pos)) {
			if (world.getBlockEntity(portalPos) instanceof PortalEntity portal) {
				return portal.interactWithItem(stack, state, world, pos, player, hand, hit);
			}
		}
		return super.onUseWithItem(stack, state, world, pos, player, hand, hit);
	}

	public static void sendDummyEndGateway(BlockPos pos, PacketContext.NotNullWithPlayer ctx) {
		var tile = new EndGatewayBlockEntity(pos, Blocks.END_GATEWAY.getDefaultState());
		var nbt = tile.toInitialChunkDataNbt(ctx.getPlayer().getRegistryManager());
		nbt.putLong("Age", 300);
		tile.read(nbt, ctx.getPlayer().getRegistryManager());
		tile.setWorld(ctx.getPlayer().getWorld());
		ctx.getPlayer().networkHandler.sendPacket(BlockEntityUpdateS2CPacket.create(tile));
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state, PacketContext ctx) {
		return Blocks.END_GATEWAY.getDefaultState();
	}

	@Override
	public void onPolymerBlockSend(BlockState blockState, BlockPos.Mutable pos, PacketContext.NotNullWithPlayer ctx) {
		sendDummyEndGateway(pos, ctx);
	}
}
