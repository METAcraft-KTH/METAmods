package nu.metacraft.lib.util.helper;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPosition;
import net.minecraft.entity.ai.pathing.LandPathNodeMaker;
import net.minecraft.entity.ai.pathing.PathContext;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import nu.metacraft.lib.METAcraftTickets;
import nu.metacraft.lib.METAcraftLibTags;
import nu.metacraft.lib.extensions.ServerPlayerEntityExtensions;
import nu.metacraft.lib.util.TaskScheduler;

import java.util.*;
import java.util.function.Consumer;

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
				player.rotate(
						target.yaw(), target.relatives().contains(PositionFlag.Y_ROT),
						target.pitch(), target.relatives().contains(PositionFlag.X_ROT)
				);
			}
		}
		var result = entity.teleportTo(target);
		TaskScheduler.scheduleImmediately(
				entity.getEntityWorld().getServer(),
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

	public static void teleportEntityToPlayer(Entity player, Entity entity) {
		teleportEntityToPlayer(player, entity, TeleportTarget.NO_OP);
	}

	public static void teleportEntityToPlayer(Entity player, Entity entity, TeleportTarget.PostDimensionTransition transition) {
		teleportEntityToPlayer(player, entity, transition, e -> {});
	}

	public static void teleportEntityToPlayer(
			Entity player, Entity entity,
			TeleportTarget.PostDimensionTransition transition,
			Consumer<Entity> onFail
	) {
		teleportEntityToPos(
				(ServerWorld) player.getEntityWorld(), player.getBlockPos(), player.getRandom(),
				player.getYaw(), player.getPitch(), player.getVelocity(), entity, transition, onFail
		);
	}

	public static void teleportEntityToPos(
			ServerWorld world, BlockPos targetPos, net.minecraft.util.math.random.Random random,
			float yaw, float pitch, Vec3d velocity,
			Entity entity, TeleportTarget.PostDimensionTransition transition, Consumer<Entity> onFail
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
		} else {
			onFail.accept(entity);
		}
	}

	public static TeleportTarget fromPlayerPos(ServerWorld world, EntityPosition pos) {
		return fromPlayerPos(world, pos, TeleportTarget.NO_OP);
	}

	public static TeleportTarget fromPlayerPos(
			ServerWorld world, EntityPosition pos, TeleportTarget.PostDimensionTransition post
	) {
		return new TeleportTarget(
				world, pos.position(), pos.deltaMovement(),
				pos.yaw(), pos.pitch(), post
		);
	}

	public static BlockPos getWorldSpawn(ServerWorld world) {
		//Basically just Mojang's function in Entity, but now it's static.
		BlockPos blockpos = world.method_74854().method_74897();
		Vec3d vec3 = blockpos.toCenterPos();
		int i = world.getWorldChunk(blockpos).sampleHeightmap(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, blockpos.getX(), blockpos.getZ()) + 1;
		return BlockPos.ofFloored(vec3.x, i, vec3.z);
	}

	public static TeleportTarget getOverworldSpawn(
			MinecraftServer server, boolean missingRespawnBlock,
			TeleportTarget.PostDimensionTransition postDimensionTransition
	) {
		var respawnWorld = server.getWorld(server.method_74945().method_74894());
		if (respawnWorld == null) {
			respawnWorld = server.getOverworld();
		}
		return new TeleportTarget(
				respawnWorld, getWorldSpawn(respawnWorld).toBottomCenterPos(),
				Vec3d.ZERO, respawnWorld.method_74854().yaw(), respawnWorld.method_74854().pitch(),
				missingRespawnBlock, false,
				Set.of(), postDimensionTransition
		);
	}
}
