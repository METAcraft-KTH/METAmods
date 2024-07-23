package se.datasektionen.mc.metacraft_dungeons.util;

import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.pathing.LandPathNodeMaker;
import net.minecraft.entity.ai.pathing.PathContext;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.TeleportTarget;
import se.datasektionen.mc.metacraft_dungeons.Tags;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;

public class Teleporter {

	public static TeleportTarget.PostDimensionTransition getTeleportPets(ServerWorld source) {
		return e -> {
			if (e instanceof ServerPlayerEntity player) {
				Teleporter.teleportPets(player, source, pet -> true);
			}
		};
	}

	public static void teleportEntityToPlayer(ServerPlayerEntity player, Entity entity) {
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
		list.removeIf(
				pos -> (
							entity instanceof MobEntity mob &&
							LandPathNodeMaker.getLandNodeType(new PathContext(player.getWorld(), mob), pos.mutableCopy()) != PathNodeType.WALKABLE
						) || player.getWorld().getBlockState(pos).isIn(Tags.PORTAL)
		);
		Vec3d target = null;
		for (var pos : list) {
			if (player.getWorld().getBlockState(pos.down()).isSolidSurface(player.getWorld(), pos, entity, Direction.UP)) {
				target = Vec3d.ofBottomCenter(pos);
			}
		}
		if (target == null) {
			target = player.getPos();
		}
		entity.teleportTo(
				new TeleportTarget(
						player.getServerWorld(), target, player.getVelocity(), player.getYaw(), player.getPitch(),
						TeleportTarget.NO_OP
				)
		);
	}

	public static void teleportPets(ServerPlayerEntity player, ServerWorld source, Predicate<Entity> teleportPet) {
		for (var pet : source.getEntitiesByType(
				TypeFilter.instanceOf(TameableEntity.class),
				entity -> player.getUuid().equals(entity.getOwnerUuid()) && !entity.isSitting()
		)) {
			if (teleportPet.test(pet)) {
				teleportEntityToPlayer(player, pet);
			}
		}
	}

}
