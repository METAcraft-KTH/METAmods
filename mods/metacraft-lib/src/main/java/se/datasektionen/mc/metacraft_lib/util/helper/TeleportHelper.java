package se.datasektionen.mc.metacraft_lib.util.helper;

import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.pathing.LandPathNodeMaker;
import net.minecraft.entity.ai.pathing.PathContext;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import se.datasektionen.mc.metacraft_lib.METAcraftTickets;
import se.datasektionen.mc.metacraft_lib.METAcraftLibTags;
import se.datasektionen.mc.metacraft_lib.extensions.ServerPlayerEntityExtensions;
import se.datasektionen.mc.metacraft_lib.util.TaskScheduler;

import java.util.*;

public class TeleportHelper {

	public static final ChunkTicketType TELEPORT_MOB_SOON = METAcraftTickets.TELEPORT_MOB_SOON;

	//Fix for players not being rotated properly and console spam when riding on vehicle while teleporting.
	public static Entity teleportEntity(Entity entity, TeleportTarget target) {
		Set<ServerPlayerEntityExtensions> players = new HashSet<>();
		for (var p : entity.getPassengersDeep()) {
			if (p instanceof ServerPlayerEntity player) {
				var ext = (ServerPlayerEntityExtensions) player;
				ext.metacraft_lib$setTeleportingOnVehicle(true);
				players.add(ext);
				player.rotate(target.yaw(), target.pitch());
			}
		}
		var result = entity.teleportTo(target);
		TaskScheduler.scheduleImmediately(
				entity.getServer(),
				() -> {
					for (var player : players) {
						player.metacraft_lib$setTeleportingOnVehicle(false);
					}
				}
		);
		return result;
	}

	private static boolean collidesWithUnsafeBlock(World world, Box box) {
		BlockPos min = BlockPos.ofFloored(box.minX, box.minY, box.minZ);
		BlockPos max = BlockPos.ofFloored(box.maxX, box.maxY, box.maxZ);
		for (var pos : BlockPos.iterate(min, max)) {
			if (world.getBlockState(pos).isIn(METAcraftLibTags.Blocks.NEVER_TELEPORT_INTO)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isPosUnsafe(BlockPos pos, World world, Entity entity, Box originBox) {
		var box = originBox.offset(pos);
		if (entity instanceof MobEntity mob) {
			if (LandPathNodeMaker.getLandNodeType(new PathContext(world, mob), pos.mutableCopy()) != PathNodeType.WALKABLE) {
				return true;
			}
		} else {
			if (!world.getBlockState(pos.down()).isSolidSurface(world, pos.down(), entity, Direction.UP)) {
				return true;
			}
		}
		if (!world.isSpaceEmpty(entity, box)) {
			return true;
		}
		if (collidesWithUnsafeBlock(world, box)) {
			return true;
		}
		return false;
	}

	public static void teleportEntityToPlayer(ServerPlayerEntity player, Entity entity) {
		teleportEntityToPlayer(player, entity, TeleportTarget.NO_OP);
	}

	public static void teleportEntityToPlayer(ServerPlayerEntity player, Entity entity, TeleportTarget.PostDimensionTransition transition) {
		teleportEntityToPos(
				player.getServerWorld(), player.getBlockPos(), player.getRandom(),
				player.getYaw(), player.getPitch(), player.getVelocity(), entity, transition
		);
	}

	public static void teleportEntityToPos(
			ServerWorld world, BlockPos targetPos, net.minecraft.util.math.random.Random random,
			float yaw, float pitch, Vec3d velocity,
			Entity entity, TeleportTarget.PostDimensionTransition transition
	) {
		List<BlockPos> list = new ArrayList<>();
		for (var pos : BlockPos.iterate(targetPos.add(-3, -1, -3), targetPos.add(3, 1, 3))) {
			list.add(pos.toImmutable());
		}
		Collections.shuffle(list, new Random() {
			@Override
			public int nextInt(int bound) {
				return random.nextInt(bound);
			}
		});
		var originBox = entity.getBoundingBox().offset(entity.getPos().multiply(-1));
		list.removeIf(pos -> isPosUnsafe(pos, world, entity, originBox));
		Vec3d target = list.isEmpty() ? null : Vec3d.ofBottomCenter(list.getFirst());
		if (target != null) {
			entity.teleportTo(
					new TeleportTarget(
							world, target, velocity,
							yaw, pitch, transition
					)
			);
		}
	}

}
