package se.datasektionen.mc.metacraft_core.block.blocks;

import eu.pb4.polymer.core.api.block.PolymerBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.NoteBlock;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.block.enums.NoteBlockInstrument;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import se.datasektionen.mc.metacraft_core.block.METAcraftBlocks;
import se.datasektionen.mc.metacraft_core.extensions.ServerPlayerEntityExtensions;
import se.datasektionen.mc.metacraft_core.lodestone.CampusLodestoneState;
import se.datasektionen.mc.metacraft_lib.util.TaskScheduler;

import java.util.Optional;

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
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity abstractPlayer, BlockHitResult hit) {
		MinecraftServer server = world.getServer();
		if (server == null || !(abstractPlayer instanceof ServerPlayerEntity player)) {
			return ActionResult.CONSUME;
		}
		var campusState = CampusLodestoneState.getInstance(server);
		RegistryKey<World> campusWorldKey = campusState.getCampusWorld();
		BlockPos campusLodestonePos = campusState.getCampusLodestonePos();

		if (campusWorldKey.equals(world.getRegistryKey()) && campusLodestonePos.isWithinDistance(pos, 20.0)) {
			// The player clicked the lodestone at campus. They should return where they came from.
			RegistryKey<World> backWorldKey = getBackWorld(player);
			BlockPos backLodestonePos = getBackLodestonePos(player);
			if (backWorldKey == null || backLodestonePos == null) {
				return this.rejected(player);
			}
			ServerWorld backWorld = server.getWorld(backWorldKey);
			if (backWorld == null) {
				return this.rejected(player);
			}
			BlockState lodestoneState = backWorld.getBlockState(backLodestonePos);
			if (lodestoneState.getBlock() != METAcraftBlocks.CAMPUS_LODESTONE) {
				return this.rejected(player);
			}
			unsetBackPos(player);
			player.playSoundToPlayer(SoundEvent.of(Identifier.of("metacraft", "tunnelbana_notis")), SoundCategory.BLOCKS, 1, 1);
			return this.findSpotAndTeleportAfterDelay(server, player, backWorld, backLodestonePos);
		} else {
			// The player wants to teleport to campus.
			// First save their current lodestone position.
			setBackPos(player, world.getRegistryKey(), pos);

			// Teleport to campus.
			ServerWorld campusWorld = server.getWorld(campusWorldKey);
			player.playSoundToPlayer(SoundEvent.of(Identifier.of("metacraft", "nasta_tekniska_hogskolan")), SoundCategory.BLOCKS, 1, 1);
			return this.findSpotAndTeleportAfterDelay(server, player, campusWorld, campusLodestonePos);
		}
	}

	private ActionResult rejected(PlayerEntity player) {
		// player.playSoundToPlayer(SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.BLOCKS, 1, 2);
		player.sendMessage(Text.translatable("block.metacraft.campus_lodestone.no_destination"), true);
		return ActionResult.FAIL;
	}

	private ActionResult findSpotAndTeleportAfterDelay(MinecraftServer server, ServerPlayerEntity player, ServerWorld world, BlockPos lodestonePos) {
		if (world == null) {
			return this.rejected(player);
		}
		Optional<Vec3d> safePosition = RespawnAnchorBlock.findRespawnPosition(EntityType.PLAYER, world, lodestonePos);
		if (safePosition.isEmpty()) {
			return this.rejected(player);
		}
		Vec3d dest = safePosition.get();
		this.teleportAfterDelay(server, player, world, dest.x, dest.y, dest.z);
		return ActionResult.SUCCESS;
	}

	private void teleportAfterDelay(MinecraftServer server, PlayerEntity player, ServerWorld world, double x, double y, double z) {
		// Play sound and give slowness for 4 seconds
		player.playSoundToPlayer(SoundEvents.BLOCK_PORTAL_TRIGGER, SoundCategory.BLOCKS, 1, 1);
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 4 * 20, 3, false, false));

		// After 1 seconds, show particles that last for 3 seconds
		TaskScheduler.schedule(server, () -> {
			world.spawnParticles(ParticleTypes.PORTAL, player.getX(), player.getY(), player.getZ(), 100, 0, 0, 0, 1);
		}, 20);

		// After 4 seconds, teleport.
		TaskScheduler.schedule(server, () -> {
			player.teleport(world, x, y, z, PositionFlag.ROT, 0, 0);
		}, 4 * 20);
	}

	/**
	 * Get the world that the player should return to when clicking the Campus
	 * Lodestone at Campus. Returns null if the player has no back location.
	 *
	 * @param player The player in question.
	 * @return The back world, or null.
	 */
	@Nullable
	public static RegistryKey<World> getBackWorld(ServerPlayerEntity player) {
		return ((ServerPlayerEntityExtensions) player).metacraft_core$getCampusLodestoneBackWorld();
	}

	/**
	 * Get the block position of the lodestone that the player used to travel to
	 * campus. This is the lodestone the player will be teleported back to when
	 * clicking the lodestone at campus. Returns null if the player has no back
	 * location.
	 *
	 * @param player The player in question.
	 * @return The block position of the lodestone, or null.
	 */
	@Nullable
	public static BlockPos getBackLodestonePos(ServerPlayerEntity player) {
		return ((ServerPlayerEntityExtensions) player).metacraft_core$getCampusLodestoneBackPos();
	}

	/**
	 * Set the block position of the lodestone that the player used to teleport to
	 * campus. This is the lodestone that the player will return to when they click
	 * the lodestone at campus.
	 *
	 * @param player The player.
	 * @param world The world the lodestone is in.
	 * @param pos The position of the lodestone.
	 */
	public static void setBackPos(ServerPlayerEntity player, RegistryKey<World> world, BlockPos pos) {
		((ServerPlayerEntityExtensions) player).metacraft_core$setCampusLodestoneBackPos(world, pos);
	}

	/**
	 * Unset the back position for the player. This signals that the player no longer
	 * has a "back" location because it was consumed.
	 *
	 * @param player The player.
	 */
	public static void unsetBackPos(ServerPlayerEntity player) {
		((ServerPlayerEntityExtensions) player).metacraft_core$unsetCampusLodestoneBackPos();
	}
}
