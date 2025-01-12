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
import net.minecraft.entity.decoration.Brightness;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
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
import se.datasektionen.mc.metacraft_core.METAcraftCore;
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

	private int interpolationDuration = 1;
	private int startInterpolation = -1;
	private int teleportDuration = 1;
	private AffineTransformation transformation = AffineTransformation.identity();

	private DisplayEntity.BillboardMode billboardMode = DisplayEntity.BillboardMode.FIXED;
	private Brightness brightness = null;
	private float viewRange = 1;
	private float shadowRadius = 0;
	private float shadowStrength = 1;
	private float width = 0;
	private float height = 0;
	private int glowColourOverride = -1;

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
		if (nbt.contains(DisplayEntity.START_INTERPOLATION_KEY)) {
			startInterpolation = nbt.getInt(DisplayEntity.START_INTERPOLATION_KEY);
		}
		if (nbt.contains(DisplayEntity.INTERPOLATION_DURATION_KEY)) {
			interpolationDuration = nbt.getInt(DisplayEntity.INTERPOLATION_DURATION_KEY);
		}
		if (nbt.contains(DisplayEntity.TELEPORT_DURATION_KEY)) {
			teleportDuration = nbt.getInt(DisplayEntity.TELEPORT_DURATION_KEY);
		}
		if (nbt.contains(DisplayEntity.TRANSFORMATION_NBT_KEY)) {
			var res = AffineTransformation.ANY_CODEC.parse(NbtOps.INSTANCE, nbt.get(DisplayEntity.TRANSFORMATION_NBT_KEY)).resultOrPartial(
					METAcraftCore.LOGGER::error
			);
			res.ifPresent(affineTransformation -> this.transformation = affineTransformation);
		}
		if (nbt.contains(DisplayEntity.BILLBOARD_NBT_KEY)) {
			var b = DisplayEntity.BillboardMode.CODEC.parse(NbtOps.INSTANCE, nbt.get(DisplayEntity.BILLBOARD_NBT_KEY)).resultOrPartial(
					METAcraftCore.LOGGER::error
			);
			b.ifPresent(mode -> billboardMode = mode);
		}
		if (nbt.contains(DisplayEntity.BRIGHTNESS_NBT_KEY)) {
			var b = Brightness.CODEC.parse(NbtOps.INSTANCE, nbt.get(DisplayEntity.BRIGHTNESS_NBT_KEY)).resultOrPartial(
					METAcraftCore.LOGGER::error
			);
			b.ifPresent(value -> brightness = value);
		} else {
			brightness = null;
		}
		if (nbt.contains(DisplayEntity.VIEW_RANGE_NBT_KEY)) {
			viewRange = nbt.getFloat(DisplayEntity.VIEW_RANGE_NBT_KEY);
		}
		if (nbt.contains(DisplayEntity.SHADOW_RADIUS_NBT_KEY)) {
			shadowRadius = nbt.getFloat(DisplayEntity.SHADOW_RADIUS_NBT_KEY);
		}
		if (nbt.contains(DisplayEntity.SHADOW_STRENGTH_NBT_KEY)) {
			shadowStrength = nbt.getFloat(DisplayEntity.SHADOW_STRENGTH_NBT_KEY);
		}
		if (nbt.contains(DisplayEntity.WIDTH_NBT_KEY)) {
			width = nbt.getFloat(DisplayEntity.WIDTH_NBT_KEY);
		}
		if (nbt.contains(DisplayEntity.HEIGHT_NBT_KEY)) {
			height = nbt.getFloat(DisplayEntity.HEIGHT_NBT_KEY);
		}
		if (nbt.contains(DisplayEntity.GLOW_COLOR_OVERRIDE_NBT_KEY)) {
			glowColourOverride = nbt.getInt(DisplayEntity.GLOW_COLOR_OVERRIDE_NBT_KEY);
		}


		if (nbt.contains(STRUCTURE, NbtElement.STRING_TYPE)) {
			var id = Identifier.tryParse(nbt.getString(STRUCTURE));
			if (id != null && getWorld() instanceof ServerWorld sw) {
				if (setFromStructure(sw.getStructureTemplateManager(), id)) {
					shouldFixDisplays = false;
				}
			}
		} else if (nbt.contains(STRUCTURE, NbtElement.COMPOUND_TYPE)) {
			var s = new StructureTemplate();
			s.readNbt(this.getRegistryManager().getOrThrow(RegistryKeys.BLOCK), nbt.getCompound(STRUCTURE));
			if (setFromStructure(s)) {
				shouldFixDisplays = false;
			}
		}
		if (nbt.contains(PASSENGER_SLOTS)) {
			DisplayRider.LIST_CODEC.parse(
					getRegistryManager().getOps(NbtOps.INSTANCE),
					nbt.get(PASSENGER_SLOTS)
			).resultOrPartial(
					METAcraftCore.LOGGER::error
			).ifPresent(data -> {
				riderSlots = data;
			});
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
		nbt.putInt(DisplayEntity.START_INTERPOLATION_KEY, startInterpolation);
		nbt.putInt(DisplayEntity.INTERPOLATION_DURATION_KEY, interpolationDuration);
		nbt.putInt(DisplayEntity.TELEPORT_DURATION_KEY, teleportDuration);
		DisplayEntity.BillboardMode.CODEC.encodeStart(NbtOps.INSTANCE, billboardMode).resultOrPartial(
				METAcraftCore.LOGGER::error
		).ifPresent(b -> nbt.put(DisplayEntity.BILLBOARD_NBT_KEY, b));
		if (brightness != null) {
			Brightness.CODEC.encodeStart(NbtOps.INSTANCE, brightness).resultOrPartial(
					METAcraftCore.LOGGER::error
			).ifPresent(brightness -> nbt.put(DisplayEntity.BRIGHTNESS_NBT_KEY, brightness));
		}
		nbt.putFloat(DisplayEntity.VIEW_RANGE_NBT_KEY, viewRange);
		nbt.putFloat(DisplayEntity.SHADOW_RADIUS_NBT_KEY, shadowRadius);
		nbt.putFloat(DisplayEntity.SHADOW_STRENGTH_NBT_KEY, shadowStrength);
		nbt.putFloat(DisplayEntity.WIDTH_NBT_KEY, width);
		nbt.putFloat(DisplayEntity.HEIGHT_NBT_KEY, height);
		nbt.putInt(DisplayEntity.GLOW_COLOR_OVERRIDE_NBT_KEY, glowColourOverride);
		AffineTransformation.ANY_CODEC.encodeStart(NbtOps.INSTANCE, transformation).resultOrPartial(
				METAcraftCore.LOGGER::error
		).ifPresent(t -> nbt.put(DisplayEntity.TRANSFORMATION_NBT_KEY, t));
		if (!riderSlots.isEmpty()) {
			DisplayRider.LIST_CODEC.encodeStart(
					getRegistryManager().getOps(NbtOps.INSTANCE),
					riderSlots
			).resultOrPartial(
					METAcraftCore.LOGGER::error
			).ifPresent(data -> {
				nbt.put(PASSENGER_SLOTS, data);
			});
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
		var d = new Display(element, pos.subtract(Vec3d.ofCenter(structure.getSize()).multiply(0.5)), yawOffset, pitchOffset);
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
			display.displayElement.setInterpolationDuration(interpolationDuration);
			display.displayElement.setTeleportDuration(teleportDuration);
			display.displayElement.setStartInterpolation(startInterpolation);
			display.applyTransformation(transformation);
			display.displayElement.setBillboardMode(billboardMode);
			display.displayElement.setBrightness(brightness);
			display.displayElement.setViewRange(viewRange);
			display.displayElement.setShadowRadius(shadowRadius);
			display.displayElement.setShadowStrength(shadowStrength);
			display.displayElement.setDisplayWidth(width);
			display.displayElement.setDisplayHeight(height);
			display.displayElement.setGlowing(this.isGlowing());
			display.displayElement.setGlowColorOverride(glowColourOverride);
		}
		if (getWorld() instanceof ServerWorld) {
			for (var display : riderSlots) {
				var world = (ServerWorld) getWorld();
				var disp = display.getDisplay(world);
				if (disp == null) continue;
				disp.setInterpolationDuration(interpolationDuration);
				disp.setTeleportDuration(teleportDuration);
				disp.setStartInterpolation(startInterpolation);
				display.applyTransformation(transformation, world);
			}
		}
		updateOffsets();
	}

	private void updateOffsets() {
		for (var d : displays) {
			d.updateOffset(this);
		}
		if (getWorld() instanceof ServerWorld) {
			for (var d : riderSlots) {
				d.updatePos(this, (ServerWorld) getWorld());
			}
		}
	}

	@Override
	public void setAngles(float yaw, float pitch) {
		super.setAngles(yaw, pitch);
		updateOffsets();
	}

	@Override
	public void updatePrevAngles() {
		if (prevYaw != getYaw() || prevPitch != getPitch()) {
			updateOffsets();
		}
		super.updatePrevAngles();
	}

	@Override
	public void tick() {
		super.tick();
		if (prevYaw != getYaw() || prevPitch != getPitch()) {
			updateOffsets();
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
		mat.mul(entity.transformation.getMatrix());
		newOffset.mulPosition(mat);
		return new Vec3d(newOffset.x, newOffset.y, newOffset.z);
	}

	public static Matrix4f applyTransformation(
			float yawOffset, float pitchOffset,
			AffineTransformation transformation
	) {
		var rotated = transformation.getMatrix();
		if (yawOffset != 0 || pitchOffset != 0) {
			var rot = new Matrix4f().rotateXYZ(
					-pitchOffset * MathHelper.RADIANS_PER_DEGREE,
					-yawOffset * MathHelper.RADIANS_PER_DEGREE,
					0
			);
			rotated = rotated.mul(rot, new Matrix4f());
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
			display.updatePositionAndAngles(
					entity.getX() + offset.getX(),
					entity.getY() + offset.getY(),
					entity.getZ() + offset.getZ(),
					entity.getYaw(),
					entity.getPitch()
			);
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
