package se.datasektionen.mc.metacraft_lib.util.helper;

import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.pathing.LandPathNodeMaker;
import net.minecraft.entity.ai.pathing.PathContext;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.util.math.*;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import se.datasektionen.mc.metacraft_lib.METAcraftLibTags;

import java.util.*;
import java.util.function.Consumer;

public class MobTeleportHelper {

	public static final ChunkTicketType<ChunkPos> TELEPORT_MOB_SOON = ChunkTicketType.create(
			"teleport_mob_soon", Comparator.comparingLong(ChunkPos::toLong), 1
	);

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
		teleportEntityToPlayer(player, entity, e -> {});
	}

	public static void teleportEntityToPlayer(ServerPlayerEntity player, Entity entity, Consumer<Entity> afterTeleport) {
		List<BlockPos> list = new ArrayList<>();
		for (var pos : BlockPos.iterate(player.getBlockPos().add(-3, -1, -3), player.getBlockPos().add(3, 1, 3))) {
			list.add(pos.toImmutable());
		}
		Collections.shuffle(list, new Random() {
			@Override
			public int nextInt(int bound) {
				return player.getRandom().nextInt(bound);
			}
		});
		var originBox = entity.getBoundingBox().offset(entity.getPos().multiply(-1));
		list.removeIf(pos -> isPosUnsafe(pos, player.getWorld(), entity, originBox));
		Vec3d target = list.isEmpty() ? null : Vec3d.ofBottomCenter(list.getFirst());
		if (target != null) {
			afterTeleport.accept(
				entity.teleportTo(
						new TeleportTarget(
								player.getServerWorld(), target, player.getVelocity(),
								player.getYaw(), player.getPitch(),
								TeleportTarget.NO_OP
						)
				)
			);
		}
	}

}
