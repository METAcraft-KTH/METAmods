package nu.metacraft.bosses.entity.entities;

import com.mojang.math.Transformation;
import eu.pb4.polymer.core.api.entity.PolymerEntity;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import nu.metacraft.core.util.DisplayEntityData;
import nu.metacraft.bosses.METAcraftBosses;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.Objects;
import java.util.Set;

public class Beam extends Entity implements PolymerEntity {

	private static final String TARGET = "target";
	private static final String THICKNESS = "thickness";

	private Vec3 target;
	private Vec3 prevTarget;
	private final ElementHolder holder = new ElementHolder();
	private final ItemDisplayElement laserItemDisplay = new ItemDisplayElement();

	private int interpolationTicks = -1;
	private int offsetInterpolationTicks = -1;
	private Vec3 currentOffset;

	private final DisplayEntityData.Item data = new DisplayEntityData.Item();
	private float thickness = 0.5f;

	public Beam(EntityType<?> type, Level world) {
		super(type, world);
		EntityAttachment.ofTicking(holder, this);
		data.setItem(new ItemStack(Items.DIAMOND));
		data.getItem().set(DataComponents.ITEM_MODEL, METAcraftBosses.getID("laser"));
		data.applyItemSettings(laserItemDisplay);
		holder.addElement(laserItemDisplay);
	}

	private float getDistance(Vec3 target) {
		return (float) this.getEffectivePos().distanceTo(target);
	}

	private Vec2 getRotationToTarget(Vec3 target) {
		if (target == null) return new Vec2(0,0);
		var effectivePos = getEffectivePos();
		double d = target.x - effectivePos.x;
		double e = target.y - effectivePos.y;
		double f = target.z - effectivePos.z;
		double g = Math.sqrt(d * d + f * f);
		var pitch = -Mth.atan2(e, g) + Math.PI/2;
		var yaw = -Mth.atan2(f, d) + Math.PI/2;
		return new Vec2((float) pitch, (float) yaw);
	}

	public void setTarget(Vec3 target) {
		if (Objects.equals(target, this.target)) return;
		if (interpolationTicks != -1) {
			prevTarget = getTarget();
		} else {
			this.prevTarget = this.target;
		}
		this.target = target;
		if (data.getInterpolationDuration() > 0 && !firstTick) {
			interpolationTicks = 0;
		}
		updateTransformation();
	}

	private Vec3 getTarget() {
		if (prevTarget == null || interpolationTicks == -1) return target;
		float delta = (float) interpolationTicks / data.getInterpolationDuration();
		float invDelta = 1 - delta;
		if (delta <= 0) {
			return prevTarget;
		}
		if (delta >= 1) {
			return target;
		}

		return new Vec3(
				prevTarget.x * invDelta + target.x * delta,
				prevTarget.y * invDelta + target.y * delta,
				prevTarget.z * invDelta + target.z * delta
		);
	}

	private Vec3 getCurrentOffset() {
		if (currentOffset == null) return Vec3.ZERO;
		var invDelta = 1 - (float) offsetInterpolationTicks / data.getTeleportDuration();
		if (invDelta >= 1) {
			return currentOffset;
		}
		if (invDelta <= 0) {
			return Vec3.ZERO;
		}
		return currentOffset.scale(invDelta);
	}

	private Vec3 getEffectivePos() {
		if (currentOffset != null) {
			return position().subtract(getCurrentOffset());
		}
		return position();
	}

	public void updateTransformation() {
		if (target == null) return;
		var target = getTarget();
		var distance = getDistance(target);
		var rot = getRotationToTarget(target);

		var matrix = new Matrix4f();
		matrix.rotateZYX(
				0, rot.y, rot.x
		);
		matrix.scale(thickness, distance, thickness);

		laserItemDisplay.setTeleportDuration(data.getTeleportDuration());
		laserItemDisplay.setStartInterpolation(0);
		laserItemDisplay.setInterpolationDuration(data.getInterpolationDuration() > 0 ? 1 : 0);
		data.applySettingsNoInterpolation(laserItemDisplay);
		laserItemDisplay.setTransformation(new Transformation(data.getTransformation().getMatrix().mul(matrix, new Matrix4f())));
	}

	@Override
	public void tick() {
		super.tick();
		boolean check = false;
		if (interpolationTicks != -1) {
			interpolationTicks++;
			check = true;
		}
		if (offsetInterpolationTicks != -1) {
			offsetInterpolationTicks++;
			check = true;
		}
		if (check) {
			updateTransformation();
			if (interpolationTicks >= data.getInterpolationDuration() || getTarget() == target) {
				interpolationTicks = -1;
			}
			if (offsetInterpolationTicks >= data.getTeleportDuration()) {
				offsetInterpolationTicks = -1;
				currentOffset = null;
			}
		}
	}

	private void onPositionUpdate(Vec3 prevPos) {
		if (data.getTeleportDuration() > 0) {
			currentOffset = getEffectivePos().subtract(prevPos);
			offsetInterpolationTicks = 0;
		}
		updateTransformation();
	}

	@Override
	public void setPos(double x, double y, double z) {
		var prevPos = position();
		boolean shouldUpdate = getX() != x || getY() != y || getZ() != z;
		super.setPos(x, y, z);
		if (shouldUpdate) {
			onPositionUpdate(prevPos);
		}
	}

	@Override
	public void snapTo(double x, double y, double z, float yaw, float pitch) {
		var prevPos = position();
		super.snapTo(x, y, z, yaw, pitch);
		onPositionUpdate(prevPos);
	}

	@Override
	public void teleportSetPosition(PositionMoveRotation pos, Set<Relative> flags) {
		var prevPos = position();
		super.teleportSetPosition(pos, flags);
		onPositionUpdate(prevPos);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {

	}

	@Override
	public boolean hurtServer(ServerLevel world, DamageSource source, float amount) {
		return false;
	}

	@Override
	protected void readAdditionalSaveData(ValueInput nbt) {
		data.load(nbt, this);
		data.applyItemSettings(laserItemDisplay);
		data.applySettings(laserItemDisplay);
		setTarget(nbt.read(TARGET, Vec3.CODEC).orElse(null));
		thickness = nbt.getFloatOr(THICKNESS, 0.5f);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput nbt) {
		nbt.storeNullable(TARGET, Vec3.CODEC, target);
		this.data.save(nbt, this);
		nbt.putFloat(THICKNESS, thickness);
	}

	@Override
	public EntityType<?> getPolymerEntityType(PacketContext packetContext) {
		return EntityType.MARKER;
	}
}
