package nu.metacraft.core.block.entities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.core.block.METAcraftBlockEntities;
import nu.metacraft.lib.util.TaskScheduler;

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
		entity.kill((ServerLevel) entity.level());
	}

	@Override
	public void onCollision(BlockState state, Level world, BlockPos pos, Entity entity) {
		TaskScheduler.scheduleImmediately(world.getServer(), () -> {
			teleport(entity); //Black holes do not check portal cooldown since doing so could cause players to get stuck.
		});
	}

	public static void tick(Level world, BlockPos pos, BlockState state, BlackHolePortalEntity blackHole) {
		if (world.isClientSide()) return;
		if (blackHole.center == null) {
			blackHole.center = BlockPos.containing(blackHole.getBoundingBox().getCenter());
		}
		var centerPos = Vec3.atCenterOf(blackHole.center);
		double particleRadius = blackHole.getBoundingBox().getSize();
		((ServerLevel) world).sendParticles(
				ParticleTypes.PORTAL, centerPos.x, centerPos.y, centerPos.z,  10 * (int) Math.round(particleRadius),
				0, 0, 0, particleRadius
		);
		var entities = world.getEntitiesOfClass(
				Entity.class, new AABB(blackHole.center).inflate(blackHole.attractionRange),
				entity -> entity.distanceToSqr(centerPos) < Math.pow(blackHole.attractionRange, 2)
		);
		for (var entity : entities) {
			if (entity instanceof ServerPlayer player && player.getAbilities().flying) {
				continue;
			}
			Vec3 toBlackHole = centerPos.subtract(entity.position());
			double dist = toBlackHole.length();
			Vec3 motionVector = toBlackHole.normalize().scale(
					Math.pow((blackHole.attractionRange - dist) / blackHole.attractionRange, 2)
			);
			entity.push(motionVector);
			entity.hurtMarked = true;
			entity.fallDistance = 0;
		}
	}

	@Override
	public void loadAdditional(ValueInput nbt) {
		center = null;
		super.loadAdditional(nbt);
		attractionRange = nbt.getDoubleOr(ATTRACTION_RANGE, 0);
	}

	@Override
	public void saveAdditional(ValueOutput nbt) {
		super.saveAdditional(nbt);
		nbt.putDouble(ATTRACTION_RANGE, attractionRange);
	}

	public void setAttractionRange(double attractionRange) {
		this.attractionRange = attractionRange;
		setChanged();
	}

}
