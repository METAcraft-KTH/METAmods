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

import java.util.Set;

public class Beam extends Entity implements PolymerEntity {

	private static final String TARGET = "target";
	private static final String THICKNESS = "thickness";

	private Vec3d target;
	private Vec3d prevTarget;
	private final ElementHolder holder = new ElementHolder();
	private final ItemDisplayElement laserItemDisplay = new ItemDisplayElement();

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

	private float getDistance() {
		if (target == null) return 0;
		return (float) this.getPos().distanceTo(target);
	}

	private Vec2f getRotationToTarget() {
		if (target == null) return new Vec2f(0,0);
		double d = target.x - getX();
		double e = target.y - getY();
		double f = target.z - getZ();
		double g = Math.sqrt(d * d + f * f);
		var pitch = -MathHelper.atan2(e, g) + Math.PI/2;
		var yaw = -MathHelper.atan2(f, d) + Math.PI/2;
		return new Vec2f((float) pitch, (float) yaw);
	}

	public void setTarget(Vec3d target) {
		this.prevTarget = this.target;
		this.target = target;
		if (target != prevTarget) {
			updateTransformation();
		}
	}

	public void updateTransformation() {
		if (target == null) return;
		var distance = getDistance();
		var rot = getRotationToTarget();

		var matrix = new Matrix4f();
		matrix.rotateZYX(
				0, rot.y, rot.x
		);
		matrix.scale(thickness, distance, thickness);

		data.applySettings(laserItemDisplay);
		laserItemDisplay.setTransformation(new AffineTransformation(data.getTransformation().getMatrix().mul(matrix)));
		prevTarget = target;
	}

	@Override
	public void setPosition(double x, double y, double z) {
		super.setPosition(x, y, z);
		updateTransformation();
	}

	@Override
	public void refreshPositionAndAngles(double x, double y, double z, float yaw, float pitch) {
		super.refreshPositionAndAngles(x, y, z, yaw, pitch);
		updateTransformation();
	}

	@Override
	public void setPosition(PlayerPosition pos, Set<PositionFlag> flags) {
		super.setPosition(pos, flags);
		updateTransformation();
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
