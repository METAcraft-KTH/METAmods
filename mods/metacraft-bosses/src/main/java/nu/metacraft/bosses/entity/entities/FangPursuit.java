package nu.metacraft.bosses.entity.entities;

import eu.pb4.polymer.core.api.entity.PolymerEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import nu.metacraft.lib.util.EntityTarget;
import nu.metacraft.lib.util.helper.EntityHelper;
import nu.metacraft.bosses.boss.attacks.SpawnEntityAttackBase;
import nu.metacraft.bosses.entity.BossEntities;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.Map;
import java.util.Optional;

public class FangPursuit extends Entity implements EntityTarget.CanSetOwner, EntityTarget.CanSetTarget, PolymerEntity {

	private static final String OWNER = "Owner";
	private static final String TARGET = "Target";
	private static final String SPEED = "Speed";

	protected EntityTarget owner = EntityTarget.create(
			level(), new EntityTarget.Context(
					false, false, false, this::discard
			)
	);

	protected EntityTarget target = EntityTarget.create(
			level(), new EntityTarget.Context(
					false, true, false, this::discard
			)
	);

	protected double speed;

	public FangPursuit(EntityType<?> type, Level world) {
		super(type, world);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {

	}

	public void setSpeed(double speed) {
		this.speed = speed;
	}

	@Override
	public void setTarget(Entity target) {
		this.target.set(target);
	}

	@Override
	public void setOwner(Entity owner) {
		this.owner.set(owner);
	}

	public static EntityHelper.SpawnEntry createSpawnEntry(
			double speed, IntProvider horizontalSpawnRange, IntProvider verticalSpawnRange
	) {
		return new EntityHelper.SpawnEntry(
				SpawnEntityAttackBase.createEntityNBTFrom(
						BossEntities.FANG_PURSUIT, SpawnEntityAttackBase.createNBTFromMap(
								Map.of(SPEED, speed)
						)
				),
				false, false,
				new EntityHelper.SpawnEntry.SpawnRules(
						Optional.empty(), EntitySpawnReason.REINFORCEMENT,
						horizontalSpawnRange, verticalSpawnRange
				),
				Optional.empty()
		);
	}

	@Override
	public void tick() {
		this.noPhysics = false;
		super.tick();
		this.noPhysics = true;
		this.setNoGravity(true);

		target.getEntity().ifPresent(entity -> {
			var facing = entity.position().subtract(this.position()).normalize();
			this.setDeltaMovement(facing.scale(speed));
			this.move(MoverType.SELF, this.getDeltaMovement());

			BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
			pos.set(this.blockPosition());
			if (speed != 0) {
				double horizontalSpeed = (speed * (1 - Math.abs(facing.y)));
				int ticksToSkip = horizontalSpeed == 0 ? 20 : Math.max((int) Math.round(0.75 / horizontalSpeed), 1);
				if (ticksToSkip > 20) {
					ticksToSkip = 20;
				}
				if (tickCount % ticksToSkip == 0) {
					if (this.level().getBlockState(pos).isAir()) {
						spawnFangs(findGroundYBelow(Mth.floor(getY())-1));
					} else {
						for (int y = Mth.floor(getY())+1; y <= level().getMaxY(); y++) {
							pos.setY(y);
							if (level().getBlockState(pos).isAir()) {
								spawnFangs(findGroundYBelow(y));
								break;
							}
						}

						for (int y = Mth.floor(getY())-1; y >= level().getMinY(); y--) {
							pos.setY(y);
							if (level().getBlockState(pos).isAir()) {
								spawnFangs(findGroundYBelow(y));
								break;
							}
						}
					}
				}
			}

			if (this.distanceTo(entity) <= 0.5) {
				var lightning = EntityType.LIGHTNING_BOLT.create(level(), EntitySpawnReason.TRIGGERED);
				lightning.snapTo(Vec3.atBottomCenterOf(entity.blockPosition()));
				this.level().addFreshEntity(lightning);
				spawnFangs(entity);
				discard();
			}
		});
	}

	@Override
	public boolean hurtServer(ServerLevel world, DamageSource source, float amount) {
		return false;
	}

	private double findGroundYBelow(int startY) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		pos.set(blockPosition());
		for (int y = startY; y >= level().getMinY(); y--) {
			pos.setY(y);
			var state = level().getBlockState(pos);
			if (state.isFaceSturdy(level(), pos, Direction.UP) && !level().isEmptyBlock(pos)) {
				var shape = state.getCollisionShape(level(), pos);
				if (!shape.isEmpty()) {
					return shape.max(Direction.Axis.Y) + y;
				}
			}
		}
		return level().getMinY();
	}

	private void spawnFangs(double y) {
		if (y == this.level().getMinY()) return;
		spawnFangs(new Vec3(getX(), y, getZ()));
	}

	private void spawnFangs(Entity entity) {
		entity.startRiding(spawnFangs(new Vec3(
				entity.getX(), findGroundYBelow(Mth.floor(entity.getY())), entity.getZ()
		)), true, true);
	}

	private Entity spawnFangs(Vec3 pos) {
		var entity = new EvokerFangs(
				level(), pos.x, pos.y, pos.z,
				getYRot(), 0, (LivingEntity) owner.getEntity().filter(
						e -> e instanceof LivingEntity
				).orElse(null)
		);
		this.level().addFreshEntity(entity);
		this.level().gameEvent(
				GameEvent.ENTITY_PLACE, pos, GameEvent.Context.of(this)
		);
		return entity;
	}

	@Override
	public void readAdditionalSaveData(ValueInput nbt) {
		owner.readNBT(nbt, OWNER);
		target.readNBT(nbt, TARGET);
		speed = nbt.getDoubleOr(SPEED, 0);
	}

	@Override
	public void addAdditionalSaveData(ValueOutput nbt) {
		owner.writeNBT(nbt, OWNER);
		target.writeNBT(nbt, TARGET);
		nbt.putDouble(SPEED, speed);
	}

	@Nullable
	@Override
	public Entity getOwner() {
		return owner.getEntity().orElse(null);
	}

	@Override
	public EntityType<?> getPolymerEntityType(PacketContext ctx) {
		return EntityType.MARKER;
	}

	@Nullable
	@Override
	public LivingEntity getTarget() {
		return (LivingEntity) target.getEntity().filter(e -> e instanceof LivingEntity).orElse(null);
	}
}
