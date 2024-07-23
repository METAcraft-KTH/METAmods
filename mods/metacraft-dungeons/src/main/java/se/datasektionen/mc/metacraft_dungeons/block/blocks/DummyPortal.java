package se.datasektionen.mc.metacraft_dungeons.block.blocks;

import eu.pb4.polymer.core.api.block.PolymerBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.EndGatewayBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import se.datasektionen.mc.metacraft_dungeons.block.block_entities.PortalEntity;

public class DummyPortal extends Block implements PolymerBlock {

	public DummyPortal(Settings settings) {
		super(settings);
	}

	@Override
	public void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
		for (BlockPos portalPos : PortalEntity.forAllNearbyPortals(world, pos)) {
			if (world.getBlockEntity(portalPos) instanceof PortalEntity portal) {
				portal.onCollision(state, world, pos, entity);
			}
		}
	}

	public static void sendDummyEndGateway(BlockPos pos, ServerPlayerEntity player) {
		var tile = new EndGatewayBlockEntity(pos, Blocks.END_GATEWAY.getDefaultState());
		var nbt = tile.toInitialChunkDataNbt(player.getRegistryManager());
		nbt.putLong("Age", 300);
		tile.read(nbt, player.getRegistryManager());
		tile.setWorld(player.getWorld());
		player.networkHandler.sendPacket(BlockEntityUpdateS2CPacket.create(tile));
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state) {
		return Blocks.END_GATEWAY.getDefaultState();
	}

	@Override
	public void onPolymerBlockSend(BlockState blockState, BlockPos.Mutable pos, ServerPlayerEntity player) {
		sendDummyEndGateway(pos, player);
	}
}
