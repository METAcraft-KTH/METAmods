package se.datasektionen.mc.metacraft_season_4.entity.entities;

import eu.pb4.polymer.core.api.entity.PolymerEntity;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.player.PlayerPosition;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import se.datasektionen.mc.metacraft_core.util.DisplayEntityData;
import se.datasektionen.mc.metacraft_season_4.Season4;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.Objects;
import java.util.Set;

public class Beam extends Entity implements PolymerEntity {

	private static final String TARGET = "target";
	private static final String THICKNESS = "thickness";

	private Vec3d target;
	private Vec3d prevTarget;
	private final ElementHolder holder = new ElementHolder();
	private final ItemDisplayElement laserItemDisplay = new ItemDisplayElement();

	private int interpolationTicks = -1;
	private int offsetInterpolationTicks = -1;
	private Vec3d currentOffset;

	private final DisplayEntityData.Item data = new DisplayEntityData.Item();
	private float thickness = 0.5f;

	public Beam(EntityType<?> type, World world) {
		super(type, world);
		EntityAttachment.ofTicking(holder, this);
		data.setItem(new ItemStack(Items.DIAMOND));
		data.getItem().set(DataComponentTypes.ITEM_MODEL, Season4.getID("laser"));
		data.applyItemSettings(laserItemDisplay);
		holder.addElement(laserItemDisplay);
	}

	private float getDistance(Vec3d target) {
		return (float) this.getEffectivePos().distanceTo(target);
	}

	private Vec2f getRotationToTarget(Vec3d target) {
		if (target == null) return new Vec2f(0,0);
		var effectivePos = getEffectivePos();
		double d = target.x - effectivePos.x;
		double e = target.y - effectivePos.y;
		double f = target.z - effectivePos.z;
		double g = Math.sqrt(d * d + f * f);
		var pitch = -MathHelper.atan2(e, g) + Math.PI/2;
		var yaw = -MathHelper.atan2(f, d) + Math.PI/2;
		return new Vec2f((float) pitch, (float) yaw);
	}

	public void setTarget(Vec3d target) {
		if (Objects.equals(target, this.target)) return;
		if (interpolationTicks != -1) {
			prevTarget = getTarget();
		} else {
			this.prevTarget = this.target;
		}
		this.target = target;
		if (data.getInterpolationDuration() > 0 && !firstUpdate) {
			interpolationTicks = 0;
		}
		updateTransformation();
	}

	private Vec3d getTarget() {
		if (prevTarget == null) return target;
		float delta = (float) interpolationTicks / data.getInterpolationDuration();
		float invDelta = 1 - delta;
		if (delta <= 0) {
			return prevTarget;
		}
		if (delta >= 1) {
			return target;
		}

		return new Vec3d(
				prevTarget.x * invDelta + target.x * delta,
				prevTarget.y * invDelta + target.y * delta,
				prevTarget.z * invDelta + target.z * delta
		);
	}

	private Vec3d getCurrentOffset() {
		if (currentOffset == null) return Vec3d.ZERO;
		var invDelta = 1 - (float) offsetInterpolationTicks / data.getTeleportDuration();
		if (invDelta >= 1) {
			return currentOffset;
		}
		if (invDelta <= 0) {
			return Vec3d.ZERO;
		}
		return currentOffset.multiply(invDelta);
	}

	private Vec3d getEffectivePos() {
		if (currentOffset != null) {
			return getPos().subtract(getCurrentOffset());
		}
		return getPos();
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
		laserItemDisplay.setTransformation(new AffineTransformation(data.getTransformation().getMatrix().mul(matrix)));
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

	private void onPositionUpdate(Vec3d prevPos) {
		currentOffset = getEffectivePos().subtract(prevPos);
		if (data.getTeleportDuration() > 0) {
			offsetInterpolationTicks = 0;
		}
		updateTransformation();
	}

	@Override
	public void setPosition(double x, double y, double z) {
		var prevPos = getPos();
		boolean shouldUpdate = getX() != x || getY() != y || getZ() != z;
		super.setPosition(x, y, z);
		if (shouldUpdate) {
			onPositionUpdate(prevPos);
		}
	}

	@Override
	public void refreshPositionAndAngles(double x, double y, double z, float yaw, float pitch) {
		var prevPos = getPos();
		super.refreshPositionAndAngles(x, y, z, yaw, pitch);
		onPositionUpdate(prevPos);
	}

	@Override
	public void setPosition(PlayerPosition pos, Set<PositionFlag> flags) {
		var prevPos = getPos();
		super.setPosition(pos, flags);
		onPositionUpdate(prevPos);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {

	}

	@Override
	public boolean damage(ServerWorld world, DamageSource source, float amount) {
		return false;
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		data.load(nbt, this);
		data.applyItemSettings(laserItemDisplay);
		data.applySettings(laserItemDisplay);
		if (nbt.contains(TARGET)) {
			Vec3d.CODEC.parse(NbtOps.INSTANCE, nbt.get(TARGET)).resultOrPartial(Season4.LOGGER::error).ifPresent(
					this::setTarget
			);
		} else {
			setTarget(null);
		}
		if (nbt.contains(THICKNESS)) {
			thickness = nbt.getFloat(THICKNESS);
		}
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		if (target != null) {
			Vec3d.CODEC.encodeStart(NbtOps.INSTANCE, target).resultOrPartial(
					Season4.LOGGER::error
			).ifPresent(res -> nbt.put(TARGET, res));
		}
		this.data.save(nbt, this);
		nbt.putFloat(THICKNESS, thickness);
	}

	@Override
	public EntityType<?> getPolymerEntityType(PacketContext packetContext) {
		return EntityType.MARKER;
	}
}
