package se.datasektionen.mc.metacraft_core.block.blocks;

import eu.pb4.polymer.core.api.block.PolymerBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.NoteBlock;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.block.enums.NoteBlockInstrument;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import se.datasektionen.mc.metacraft_core.block.METAcraftBlocks;
import se.datasektionen.mc.metacraft_core.lodestone.CampusLodestoneState;

import java.util.Optional;
import java.util.Set;

public class CampusLodestoneBlock extends Block implements PolymerBlock {
	public CampusLodestoneBlock(Settings settings) {
		super(settings);
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state) {
		// See resource pack. powered harp with note 1 renders the correct model.
		return Blocks.NOTE_BLOCK.getDefaultState()
			.with(NoteBlock.INSTRUMENT, NoteBlockInstrument.HARP)
			.with(NoteBlock.NOTE, 1)
			.with(NoteBlock.POWERED, true);
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		MinecraftServer server = world.getServer();
		if (server == null) {
			return ActionResult.PASS;
		}
		var campusState = CampusLodestoneState.getInstance(server);
		var campusLocation = campusState.getCampusLocation();
		if (campusLocation.world().equals(world.getRegistryKey()) && campusLocation.lodestonePos().isWithinDistance(pos, 20.0)) {
			// The player clicked the lodestone at campus. They should return where they came from.
			var backLocation = campusState.getBackLocation(player);
			if (backLocation == null) {
				player.playSoundToPlayer(SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.BLOCKS, 1, 2);
				return ActionResult.FAIL;
			}
			ServerWorld backWorld = server.getWorld(backLocation.world());
			BlockState lodestoneState = backWorld.getBlockState(backLocation.lodestonePos());
			if (lodestoneState.getBlock() != METAcraftBlocks.CAMPUS_LODESTONE) {
				player.playSoundToPlayer(SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.BLOCKS, 1, 2);
				return ActionResult.FAIL;
			}
			Optional<Vec3d> respawnPosition = RespawnAnchorBlock.findRespawnPosition(EntityType.PLAYER, backWorld, backLocation.lodestonePos());
			if (respawnPosition.isEmpty()) {
				player.playSoundToPlayer(SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.BLOCKS, 1, 2);
				return ActionResult.FAIL;
			}
			Vec3d dest = respawnPosition.get();
			campusState.setBackLocation(player, null);
			player.teleport(backWorld, dest.x, dest.y, dest.z, Set.of(), 0, 0);
		} else {
			// The player wants to teleport to campus.
			// First save their current lodestone position.
			campusState.setBackLocation(player, new CampusLodestoneState.Location(world.getRegistryKey(), pos));

			// Teleport to campus.
			ServerWorld campusWorld = server.getWorld(campusLocation.world());
			BlockPos dest = campusLocation.lodestonePos();
			player.teleport(campusWorld, dest.getX(), dest.getY(), dest.getZ(), Set.of(), 0, 0);
		}
		return ActionResult.CONSUME;
	}
}
