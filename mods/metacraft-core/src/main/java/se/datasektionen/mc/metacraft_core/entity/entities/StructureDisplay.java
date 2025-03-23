package se.datasektionen.mc.metacraft_core.entity.entities;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import eu.pb4.polymer.core.api.entity.PolymerEntity;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import eu.pb4.polymer.virtualentity.api.elements.BlockDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.DisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import net.minecraft.block.FluidBlock;
import net.minecraft.block.SkullBlock;
import net.minecraft.block.WallSkullBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import se.datasektionen.mc.metacraft_core.util.DisplayEntityData;
import se.datasektionen.mc.metacraft_lib.mixin.AccessorStructureTemplate;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class StructureDisplay extends Entity implements PolymerEntity {

	private static final String STRUCTURE = "structure";
	private static final String PASSENGER_SLOTS = "passenger_slots";

	private ElementHolder holder = new ElementHolder();
	private final List<Display> displays = new ArrayList<>();
	private List<DisplayRider> riderSlots = new ArrayList<>();

	private final DisplayEntityData data = new DisplayEntityData();

	private Identifier structureID;
	private StructureTemplate structure = new StructureTemplate();

	public StructureDisplay(EntityType<?> type, World world) {
		super(type, world);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {

	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		boolean shouldFixDisplays = true;
		data.load(nbt, this);

		var idString = nbt.getString(STRUCTURE);
		if (idString.isPresent()) {
			var id = Identifier.tryParse(idString.get());
			if (id != null && getWorld() instanceof ServerWorld sw) {
				if (setFromStructure(sw.getStructureTemplateManager(), id)) {
					shouldFixDisplays = false;
				}
			}
		} else {
			var s = new StructureTemplate();
			s.readNbt(this.getRegistryManager().getOrThrow(RegistryKeys.BLOCK), nbt.getCompoundOrEmpty(STRUCTURE));
			if (setFromStructure(s)) {
				shouldFixDisplays = false;
			}
		}
		if (nbt.contains(PASSENGER_SLOTS)) {
			nbt.get(PASSENGER_SLOTS, DisplayRider.LIST_CODEC, getRegistryManager().getOps(NbtOps.INSTANCE)).ifPresent(
					data -> riderSlots = data
			);
		} else {
			riderSlots = new ArrayList<>();
		}
		if (shouldFixDisplays) {
			refreshDisplayValues();
		}
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		if (structureID != null) {
			nbt.putString(STRUCTURE, structureID.toString());
		} else {
			nbt.put(STRUCTURE, structure.writeNbt(new NbtCompound()));
		}
		data.save(nbt, this);
		if (!riderSlots.isEmpty()) {
			nbt.put(PASSENGER_SLOTS, DisplayRider.LIST_CODEC, getRegistryManager().getOps(NbtOps.INSTANCE), riderSlots);
		}
	}

	public boolean setFromStructure(StructureTemplateManager manager, Identifier id) {
		return manager.getTemplate(id).map(
				structure -> {
					var result = setFromStructure(structure);
					structureID = id;
					return result;
				}
		).orElse(false);
	}

	private boolean needsUpdate(StructureTemplate lhs, StructureTemplate rhs) {
		if (!lhs.getSize().equals(rhs.getSize())) {
			return true;
		}
		var lhsLists = ((AccessorStructureTemplate) lhs).getBlockInfoLists();
		var rhsLists = ((AccessorStructureTemplate) rhs).getBlockInfoLists();
		if (lhsLists.size() != rhsLists.size()) {
			return true;
		}
		for (int i = 0; i < lhsLists.size(); i++) {
			var lhsList = lhsLists.get(i).getAll();
			var rhsList = rhsLists.get(i).getAll();
			if (lhsList.size() != rhsList.size()) {
				return true;
			}
			for (int j = 0; j < lhsList.size(); j++) {
				if (!lhsList.get(j).equals(rhsList.get(j))) {
					return true;
				}
			}
		}
		return false;
	}

	public boolean setFromStructure(StructureTemplate structure) {
		structureID = null;
		var prev = this.structure;
		this.structure = structure;
		if (needsUpdate(prev, structure)) {
			updateStructure();
			return true;
		}
		return false;
	}

	private void addDisplay(DisplayElement element, Vec3d pos) {
		addDisplay(element, pos, 0, 0);
	}

	private void addDisplay(DisplayElement element, Vec3d pos, float yawOffset, float pitchOffset) {
		var d = new Display(
				element, pos.subtract(
						structure.getSize().getX()*0.5,
						0,
						structure.getSize().getZ()*0.5
				),
				yawOffset, pitchOffset
		);
		displays.add(d);
		holder.addElement(element);
	}

	private void updateStructure() {
		holder.destroy();
		holder = new ElementHolder();

		for (var list : ((AccessorStructureTemplate) structure).getBlockInfoLists()) {
			for (var l : list.getAll()) {
				if (l.state().isAir() || l.state().getBlock() instanceof FluidBlock) continue;
				if (l.state().getBlock() instanceof SkullBlock skullBlock) {
					var itemDisplay = new ItemDisplayElement();
					itemDisplay.setItem(new ItemStack(skullBlock.asItem()));
					float yaw = MathHelper.wrapDegrees(l.state().get(SkullBlock.ROTATION) * 360.0f/16);
					addDisplay(itemDisplay, Vec3d.ofCenter(l.pos()), yaw, 0);
					continue;
				}
				if (l.state().getBlock() instanceof WallSkullBlock skullBlock) {
					var itemDisplay = new ItemDisplayElement();
					itemDisplay.setItem(new ItemStack(skullBlock.asItem()));
					var facing = l.state().get(WallSkullBlock.FACING);
					addDisplay(
							itemDisplay,
							Vec3d.add(
									l.pos(),
									0.5 - facing.getOffsetX() * 0.25,
									0.75,
									0.5 - facing.getOffsetZ() * 0.25
							),
							Direction.getHorizontalDegreesOrThrow(facing.getOpposite()), 0
					);
					continue;
				}
				var blockDisplay = new BlockDisplayElement();
				blockDisplay.setBlockState(l.state());
				addDisplay(blockDisplay, Vec3d.of(l.pos()));
			}
		}
		refreshDisplayValues();
		initAttachment();
	}

	private void refreshDisplayValues() {
		for (var display : displays) {
			data.applySettings(display.displayElement);
			display.applyTransformation(data.getTransformation());
		}
		if (getWorld() instanceof ServerWorld) {
			for (var display : riderSlots) {
				var world = (ServerWorld) getWorld();
				var disp = display.getDisplay(world);
				if (disp == null) continue;
				disp.setInterpolationDuration(data.getInterpolationDuration());
				disp.setTeleportDuration(data.getTeleportDuration());
				disp.setStartInterpolation(data.getStartInterpolation());
				display.applyTransformation(data.getTransformation(), world);
			}
		}
		updateOffsets();
	}

	private void updatePositions() {
		if (getWorld() instanceof ServerWorld) {
			for (var d : riderSlots) {
				d.updatePos(this, (ServerWorld) getWorld());
			}
		}
	}

	private void updateOffsets() {
		for (var d : displays) {
			d.updateOffset(this);
		}
		updatePositions();
	}

	@Override
	public void setAngles(float yaw, float pitch) {
		super.setAngles(yaw, pitch);
		updateOffsets();
	}

	@Override
	public void updateLastAngles() {
		if (lastYaw != getYaw() || lastPitch != getPitch()) {
			updateOffsets();
		}
		super.updateLastAngles();
	}

	@Override
	public void tick() {
		super.tick();
		if (lastYaw != getYaw() || lastPitch != getPitch()) {
			updateOffsets();
		}
		if (lastX != getX() || lastY != getY() || lastZ != getZ()) {
			updatePositions();
		}
	}

	@Override
	public boolean damage(ServerWorld world, DamageSource source, float amount) {
		return false;
	}

	private void initAttachment() {
		EntityAttachment.ofTicking(holder, this);
	}

	@Override
	public EntityType<?> getPolymerEntityType(PacketContext ctx) {
		return EntityType.MARKER;
	}

	public static Vec3d applyOffset(
			StructureDisplay entity, Vec3d offset
	) {
		var mat = new Matrix4f();
		mat.rotateYXZ(
				-entity.getYaw() * MathHelper.RADIANS_PER_DEGREE,
				entity.getPitch() * MathHelper.RADIANS_PER_DEGREE,
				0
		);
		Vector3d newOffset = new Vector3d(offset.x, offset.y, offset.z);
		var transformation = new AffineTransformation(
				null,
				entity.data.getTransformation().getLeftRotation(),
				entity.data.getTransformation().getScale(),
				entity.data.getTransformation().getRightRotation()
		);
		mat.mul(transformation.getMatrix());
		newOffset.mulPosition(mat);
		return new Vec3d(newOffset.x, newOffset.y, newOffset.z);
	}

	public static Matrix4f applyTransformation(
			float yawOffset, float pitchOffset,
			AffineTransformation transformation
	) {
		var rotated = transformation.copyMatrix();
		if (yawOffset != 0 || pitchOffset != 0) {
			var rot = new Matrix4f().rotateXYZ(
					-pitchOffset * MathHelper.RADIANS_PER_DEGREE,
					-yawOffset * MathHelper.RADIANS_PER_DEGREE,
					0
			);
			rotated = rotated.mul(rot);
		}
		return rotated;
	}

	public record Display(DisplayElement displayElement, Vec3d offset, float yawOffset, float pitchOffset) {
		public void updateOffset(StructureDisplay entity) {
			displayElement.setOffset(
					StructureDisplay.applyOffset(entity, offset)
			);
			displayElement.setYaw(entity.getYaw());
			displayElement.setPitch(entity.getPitch());
		}

		public void applyTransformation(AffineTransformation transformation) {
			displayElement.setTransformation(
					StructureDisplay.applyTransformation(
							yawOffset, pitchOffset, transformation
					)
			);
		}
	}

	public static class DisplayRider {

		public static final Codec<DisplayRider> CODEC = RecordCodecBuilder.create(
				instance -> instance.group(
						Uuids.CODEC.fieldOf("entity").forGetter(t -> t.entity),
						Vec3d.CODEC.fieldOf("offset").forGetter(t -> t.offset),
						Codec.FLOAT.fieldOf("yaw_offset").forGetter(t -> t.yawOffset),
						Codec.FLOAT.fieldOf("pitch_offset").forGetter(t -> t.pitchOffset)
				).apply(instance, DisplayRider::new)
		);

		public static final Codec<List<DisplayRider>> LIST_CODEC = CODEC.listOf();

		public final UUID entity;
		public final Vec3d offset;
		public final float yawOffset;
		public final float pitchOffset;

		@Nullable
		private DisplayEntity disp;

		public DisplayRider(UUID entity, Vec3d offset, float yawOffset, float pitchOffset) {
			this.entity = entity;
			this.offset = offset;
			this.yawOffset = yawOffset;
			this.pitchOffset = pitchOffset;
		}

		@Nullable
		public DisplayEntity getDisplay(ServerWorld world) {
			if (disp == null || !disp.isAlive()) {
				var e = world.getEntity(entity);
				if (e instanceof DisplayEntity d) {
					this.disp = d;
				}
			}
			return disp;
		}

		public void updatePos(StructureDisplay entity, ServerWorld world) {
			var offset = StructureDisplay.applyOffset(entity, this.offset);
			var display = getDisplay(world);
			if (display == null) return;
			var prevYaw = display.getYaw();
			var prevPitch = display.getPitch();
			display.updatePositionAndAngles(
					entity.getX() + offset.getX(),
					entity.getY() + offset.getY(),
					entity.getZ() + offset.getZ(),
					entity.getYaw(),
					entity.getPitch()
			);
			var yawDiff = entity.getYaw() - prevYaw;
			var pitchDiff = entity.getPitch() - prevPitch;
			display.getPassengersDeep().forEach(p -> {
				p.setYaw(p.getYaw() + yawDiff);
				p.setBodyYaw(p.getBodyYaw() + yawDiff);
				p.setHeadYaw(p.getHeadYaw() + yawDiff);
				p.setPitch(p.getPitch() + pitchDiff);
			});
		}

		public void applyTransformation(
				AffineTransformation transformation, ServerWorld world
		) {
			var disp = getDisplay(world);
			if (disp == null) return;
			disp.setTransformation(
					new AffineTransformation(
							StructureDisplay.applyTransformation(
									yawOffset, pitchOffset, transformation
							)
					)
			);
		}
	}

}
