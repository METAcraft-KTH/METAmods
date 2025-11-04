package nu.metacraft.lib.util.helper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.lib.METAcraftTickets;
import nu.metacraft.lib.METAcraftLibTags;
import nu.metacraft.lib.extensions.ServerPlayerEntityExtensions;
import nu.metacraft.lib.util.TaskScheduler;

import java.util.*;
import java.util.function.Consumer;

public class TeleportHelper {

	public static final TicketType TELEPORT_MOB_SOON = METAcraftTickets.TELEPORT_MOB_SOON;

	//Fix for players not being rotated properly and console spam when riding on vehicle while teleporting.
	public static Entity teleportEntity(Entity entity, TeleportTransition target) {
		Set<ServerPlayerEntityExtensions> players = new HashSet<>();
		for (var p : entity.getIndirectPassengers()) {
			if (p instanceof ServerPlayer player) {
				var ext = (ServerPlayerEntityExtensions) player;
				ext.metacraft_lib$setTeleportingOnVehicle(true);
				players.add(ext);
				player.forceSetRotation(
						target.yRot(), target.relatives().contains(Relative.Y_ROT),
						target.xRot(), target.relatives().contains(Relative.X_ROT)
				);
			}
		}
		var result = entity.teleport(target);
		TaskScheduler.scheduleImmediately(
				entity.level().getServer(),
				() -> {
					for (var player : players) {
						player.metacraft_lib$setTeleportingOnVehicle(false);
					}
				}
		);
		return result;
	}

	private static boolean collidesWithUnsafeBlock(Level world, AABB box) {
		BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ);
		BlockPos max = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
		for (var pos : BlockPos.betweenClosed(min, max)) {
			if (world.getBlockState(pos).is(METAcraftLibTags.Blocks.NEVER_TELEPORT_INTO)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isPosUnsafe(BlockPos pos, Level world, Entity entity, AABB originBox) {
		var box = originBox.move(pos);
		if (entity instanceof Mob mob) {
			if (WalkNodeEvaluator.getPathTypeStatic(new PathfindingContext(world, mob), pos.mutable()) != PathType.WALKABLE) {
				return true;
			}
		} else {
			if (!world.getBlockState(pos.below()).entityCanStandOnFace(world, pos.below(), entity, Direction.UP)) {
				return true;
			}
		}
		if (!world.noCollision(entity, box)) {
			return true;
		}
		return collidesWithUnsafeBlock(world, box);
	}

	public static void teleportEntityToPlayer(Entity player, Entity entity) {
		teleportEntityToPlayer(player, entity, TeleportTransition.DO_NOTHING);
	}

	public static void teleportEntityToPlayer(Entity player, Entity entity, TeleportTransition.PostTeleportTransition transition) {
		teleportEntityToPlayer(player, entity, transition, e -> {});
	}

	public static void teleportEntityToPlayer(
			Entity player, Entity entity,
			TeleportTransition.PostTeleportTransition transition,
			Consumer<Entity> onFail
	) {
		teleportEntityToPos(
				(ServerLevel) player.level(), player.blockPosition(), player.getRandom(),
				player.getYRot(), player.getXRot(), player.getDeltaMovement(), entity, transition, onFail
		);
	}

	public static void teleportEntityToPos(
			ServerLevel world, BlockPos targetPos, net.minecraft.util.RandomSource random,
			float yaw, float pitch, Vec3 velocity,
			Entity entity, TeleportTransition.PostTeleportTransition transition, Consumer<Entity> onFail
	) {
		List<BlockPos> list = new ArrayList<>();
		for (var pos : BlockPos.betweenClosed(targetPos.offset(-3, -1, -3), targetPos.offset(3, 1, 3))) {
			list.add(pos.immutable());
		}
		Collections.shuffle(list, new Random() {
			@Override
			public int nextInt(int bound) {
				return random.nextInt(bound);
			}
		});
		var originBox = entity.getBoundingBox().move(entity.position().scale(-1));
		list.removeIf(pos -> isPosUnsafe(pos, world, entity, originBox));
		Vec3 target = list.isEmpty() ? null : Vec3.atBottomCenterOf(list.getFirst());
		if (target != null) {
			entity.teleport(
					new TeleportTransition(
							world, target, velocity,
							yaw, pitch, transition
					)
			);
		} else {
			onFail.accept(entity);
		}
	}

	public static TeleportTransition fromPlayerPos(ServerLevel world, PositionMoveRotation pos) {
		return fromPlayerPos(world, pos, TeleportTransition.DO_NOTHING);
	}

	public static TeleportTransition fromPlayerPos(
			ServerLevel world, PositionMoveRotation pos, TeleportTransition.PostTeleportTransition post
	) {
		return new TeleportTransition(
				world, pos.position(), pos.deltaMovement(),
				pos.yRot(), pos.xRot(), post
		);
	}

	public static BlockPos getWorldSpawn(ServerLevel world) {
		//Basically just Mojang's function in Entity, but now it's static.
		BlockPos blockpos = world.getRespawnData().pos();
		Vec3 vec3 = blockpos.getCenter();
		int i = world.getChunkAt(blockpos).getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockpos.getX(), blockpos.getZ()) + 1;
		return BlockPos.containing(vec3.x, i, vec3.z);
	}

	public static TeleportTransition getOverworldSpawn(
			MinecraftServer server, boolean missingRespawnBlock,
			TeleportTransition.PostTeleportTransition postDimensionTransition
	) {
		var respawnWorld = server.getLevel(server.getRespawnData().dimension());
		if (respawnWorld == null) {
			respawnWorld = server.overworld();
		}
		return new TeleportTransition(
				respawnWorld, getWorldSpawn(respawnWorld).getBottomCenter(),
				Vec3.ZERO, respawnWorld.getRespawnData().yaw(), respawnWorld.getRespawnData().pitch(),
				missingRespawnBlock, false,
				Set.of(), postDimensionTransition
		);
	}
}
