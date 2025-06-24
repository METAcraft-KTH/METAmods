package se.metacraft.bosses.entity.entities;

import eu.pb4.polymer.core.api.entity.PolymerEntity;
import net.minecraft.entity.*;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.mob.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.intprovider.IntProvider;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import org.jetbrains.annotations.Nullable;
import se.datasektionen.mc.metacraft_lib.util.EntityTarget;
import se.datasektionen.mc.metacraft_lib.util.helper.EntityHelper;
import se.metacraft.bosses.boss.attacks.SpawnEntityAttackBase;
import se.metacraft.bosses.entity.BossEntities;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.Map;
import java.util.Optional;

public class FangPursuit extends Entity implements EntityTarget.CanSetOwner, EntityTarget.CanSetTarget, PolymerEntity {

	private static final String OWNER = "Owner";
	private static final String TARGET = "Target";
	private static final String SPEED = "Speed";

	protected EntityTarget owner = EntityTarget.create(
			getWorld(), new EntityTarget.Context(
					false, false, false, this::discard
			)
	);

	protected EntityTarget target = EntityTarget.create(
			getWorld(), new EntityTarget.Context(
					false, true, false, this::discard
			)
	);

	protected double speed;

	public FangPursuit(EntityType<?> type, World world) {
		super(type, world);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {

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
						Optional.empty(), SpawnReason.REINFORCEMENT,
						horizontalSpawnRange, verticalSpawnRange
				),
				Optional.empty()
		);
	}

	@Override
	public void tick() {
		this.noClip = false;
		super.tick();
		this.noClip = true;
		this.setNoGravity(true);

		target.getEntity().ifPresent(entity -> {
			var facing = entity.getPos().subtract(this.getPos()).normalize();
			this.setVelocity(facing.multiply(speed));
			this.move(MovementType.SELF, this.getVelocity());

			BlockPos.Mutable pos = new BlockPos.Mutable();
			pos.set(this.getBlockPos());
			if (speed != 0) {
				double horizontalSpeed = (speed * (1 - Math.abs(facing.y)));
				int ticksToSkip = horizontalSpeed == 0 ? 20 : Math.max((int) Math.round(0.75 / horizontalSpeed), 1);
				if (ticksToSkip > 20) {
					ticksToSkip = 20;
				}
				if (age % ticksToSkip == 0) {
					if (this.getWorld().getBlockState(pos).isAir()) {
						spawnFangs(findGroundYBelow(MathHelper.floor(getY())-1));
					} else {
						for (int y = MathHelper.floor(getY())+1; y <= getWorld().getTopYInclusive(); y++) {
							pos.setY(y);
							if (getWorld().getBlockState(pos).isAir()) {
								spawnFangs(findGroundYBelow(y));
								break;
							}
						}

						for (int y = MathHelper.floor(getY())-1; y >= getWorld().getBottomY(); y--) {
							pos.setY(y);
							if (getWorld().getBlockState(pos).isAir()) {
								spawnFangs(findGroundYBelow(y));
								break;
							}
						}
					}
				}
			}

			if (this.distanceTo(entity) <= 0.5) {
				var lightning = EntityType.LIGHTNING_BOLT.create(getWorld(), SpawnReason.TRIGGERED);
				lightning.refreshPositionAfterTeleport(Vec3d.ofBottomCenter(entity.getBlockPos()));
				this.getWorld().spawnEntity(lightning);
				spawnFangs(entity);
				discard();
			}
		});
	}

	@Override
	public boolean damage(ServerWorld world, DamageSource source, float amount) {
		return false;
	}

	private double findGroundYBelow(int startY) {
		BlockPos.Mutable pos = new BlockPos.Mutable();
		pos.set(getBlockPos());
		for (int y = startY; y >= getWorld().getBottomY(); y--) {
			pos.setY(y);
			var state = getWorld().getBlockState(pos);
			if (state.isSideSolidFullSquare(getWorld(), pos, Direction.UP) && !getWorld().isAir(pos)) {
				var shape = state.getCollisionShape(getWorld(), pos);
				if (!shape.isEmpty()) {
					return shape.getMax(Direction.Axis.Y) + y;
				}
			}
		}
		return getWorld().getBottomY();
	}

	private void spawnFangs(double y) {
		if (y == this.getWorld().getBottomY()) return;
		spawnFangs(new Vec3d(getX(), y, getZ()));
	}

	private void spawnFangs(Entity entity) {
		entity.startRiding(spawnFangs(new Vec3d(
				entity.getX(), findGroundYBelow(MathHelper.floor(entity.getY())), entity.getZ()
		)), true);
	}

	private Entity spawnFangs(Vec3d pos) {
		var entity = new EvokerFangsEntity(
				getWorld(), pos.x, pos.y, pos.z,
				getYaw(), 0, (LivingEntity) owner.getEntity().filter(
						e -> e instanceof LivingEntity
				).orElse(null)
		);
		this.getWorld().spawnEntity(entity);
		this.getWorld().emitGameEvent(
				GameEvent.ENTITY_PLACE, pos, GameEvent.Emitter.of(this)
		);
		return entity;
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		owner.readNBT(nbt, OWNER);
		target.readNBT(nbt, TARGET);
		speed = nbt.getDouble(SPEED, 0);
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
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
