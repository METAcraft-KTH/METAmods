package se.datasektionen.mc.metacraft_core.block.entities;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import se.datasektionen.mc.metacraft_core.block.METAcraftBlockEntities;
import se.datasektionen.mc.metacraft_lib.util.TaskScheduler;

public class BlackHolePortalEntity extends PortalEntity {

	private static final String ATTRACTION_RANGE = "AttractionRange";

	protected BlockPos center;
	protected double attractionRange;

	public BlackHolePortalEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	public BlackHolePortalEntity(BlockPos pos, BlockState state) {
		super(METAcraftBlockEntities.BLACK_HOLE, pos, state);
	}

	@Override
	protected void onTeleportFail(Entity entity) {
		entity.kill((ServerWorld) entity.getWorld());
	}

	@Override
	public void onCollision(BlockState state, World world, BlockPos pos, Entity entity) {
		TaskScheduler.scheduleImmediately(world.getServer(), () -> {
			teleport(entity); //Black holes do not check portal cooldown since doing so could cause players to get stuck.
		});
	}

	public static void tick(World world, BlockPos pos, BlockState state, BlackHolePortalEntity blackHole) {
		if (world.isClient) return;
		if (blackHole.center == null) {
			blackHole.center = BlockPos.ofFloored(blackHole.getBoundingBox().getCenter());
		}
		var centerPos = blackHole.center.toCenterPos();
		double particleRadius = blackHole.getBoundingBox().getAverageSideLength();
		((ServerWorld) world).spawnParticles(
				ParticleTypes.PORTAL, centerPos.x, centerPos.y, centerPos.z,  10 * (int) Math.round(particleRadius),
				0, 0, 0, particleRadius
		);
		var entities = world.getEntitiesByClass(
				Entity.class, new Box(blackHole.center).expand(blackHole.attractionRange),
				entity -> entity.squaredDistanceTo(centerPos) < Math.pow(blackHole.attractionRange, 2)
		);
		for (var entity : entities) {
			if (entity instanceof ServerPlayerEntity player && player.getAbilities().flying) {
				continue;
			}
			Vec3d toBlackHole = centerPos.subtract(entity.getPos());
			double dist = toBlackHole.length();
			Vec3d motionVector = toBlackHole.normalize().multiply(
					Math.pow((blackHole.attractionRange - dist) / blackHole.attractionRange, 2)
			);
			entity.addVelocity(motionVector);
			entity.velocityModified = true;
			entity.fallDistance = 0;
		}
	}

	@Override
	public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		center = null;
		super.readNbt(nbt, lookup);
		attractionRange = nbt.getDouble(ATTRACTION_RANGE, 0);
	}

	@Override
	public void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		super.writeNbt(nbt, lookup);
		nbt.putDouble(ATTRACTION_RANGE, attractionRange);
	}

	public void setAttractionRange(double attractionRange) {
		this.attractionRange = attractionRange;
		markDirty();
	}

}
