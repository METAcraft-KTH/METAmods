package nu.metacraft.core.entity.entities;

import com.mojang.math.Transformation;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import eu.pb4.polymer.core.api.entity.PolymerEntity;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import eu.pb4.polymer.virtualentity.api.elements.BlockDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.DisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.WallSkullBlock;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import nu.metacraft.core.util.DisplayEntityData;
import nu.metacraft.lib.mixin.StructureTemplateAccessor;
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

	private ResourceLocation structureID;
	private StructureTemplate structure = new StructureTemplate();

	public StructureDisplay(EntityType<?> type, Level world) {
		super(type, world);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {

	}

	@Override
	protected void readAdditionalSaveData(ValueInput nbt) {
		boolean shouldFixDisplays = true;
		data.load(nbt, this);

		var idString = nbt.getString(STRUCTURE);
		if (idString.isPresent()) {
			var id = ResourceLocation.tryParse(idString.get());
			if (id != null && level() instanceof ServerLevel sw) {
				if (setFromStructure(sw.getStructureManager(), id)) {
					shouldFixDisplays = false;
				}
			}
		} else {
			var data = nbt.read(STRUCTURE, CompoundTag.CODEC).orElse(null);
			if (data != null) {
				var s = new StructureTemplate();
				s.load(this.registryAccess().lookupOrThrow(Registries.BLOCK), data);
				if (setFromStructure(s)) {
					shouldFixDisplays = false;
				}
			}
		}
		nbt.read(PASSENGER_SLOTS, DisplayRider.LIST_CODEC).ifPresentOrElse(
				data -> riderSlots = data,
				() -> riderSlots = new ArrayList<>()
		);
		if (shouldFixDisplays) {
			refreshDisplayValues();
		}
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput nbt) {
		if (structureID != null) {
			nbt.putString(STRUCTURE, structureID.toString());
		} else {
			nbt.store(STRUCTURE, CompoundTag.CODEC, structure.save(new CompoundTag()));
		}
		data.save(nbt, this);
		if (!riderSlots.isEmpty()) {
			nbt.store(PASSENGER_SLOTS, DisplayRider.LIST_CODEC, riderSlots);
		}
	}

	public boolean setFromStructure(StructureTemplateManager manager, ResourceLocation id) {
		return manager.get(id).map(
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
		var lhsLists = ((StructureTemplateAccessor) lhs).getPalettes();
		var rhsLists = ((StructureTemplateAccessor) rhs).getPalettes();
		if (lhsLists.size() != rhsLists.size()) {
			return true;
		}
		for (int i = 0; i < lhsLists.size(); i++) {
			var lhsList = lhsLists.get(i).blocks();
			var rhsList = rhsLists.get(i).blocks();
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

	private void addDisplay(DisplayElement element, Vec3 pos) {
		addDisplay(element, pos, 0, 0);
	}

	private void addDisplay(DisplayElement element, Vec3 pos, float yawOffset, float pitchOffset) {
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

		for (var list : ((StructureTemplateAccessor) structure).getPalettes()) {
			for (var l : list.blocks()) {
				if (l.state().isAir() || l.state().getBlock() instanceof LiquidBlock) continue;
				if (l.state().getBlock() instanceof SkullBlock skullBlock) {
					var itemDisplay = new ItemDisplayElement();
					itemDisplay.setItem(new ItemStack(skullBlock.asItem()));
					float yaw = Mth.wrapDegrees(l.state().getValue(SkullBlock.ROTATION) * 360.0f/16);
					addDisplay(itemDisplay, Vec3.atCenterOf(l.pos()), yaw, 0);
					continue;
				}
				if (l.state().getBlock() instanceof WallSkullBlock skullBlock) {
					var itemDisplay = new ItemDisplayElement();
					itemDisplay.setItem(new ItemStack(skullBlock.asItem()));
					var facing = l.state().getValue(WallSkullBlock.FACING);
					addDisplay(
							itemDisplay,
							Vec3.atLowerCornerWithOffset(
									l.pos(),
									0.5 - facing.getStepX() * 0.25,
									0.75,
									0.5 - facing.getStepZ() * 0.25
							),
							Direction.getYRot(facing.getOpposite()), 0
					);
					continue;
				}
				var blockDisplay = new BlockDisplayElement();
				blockDisplay.setBlockState(l.state());
				addDisplay(blockDisplay, Vec3.atLowerCornerOf(l.pos()));
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
		if (level() instanceof ServerLevel) {
			for (var display : riderSlots) {
				var world = (ServerLevel) level();
				var disp = display.getDisplay(world);
				if (disp == null) continue;
				disp.setTransformationInterpolationDuration(data.getInterpolationDuration());
				disp.setPosRotInterpolationDuration(data.getTeleportDuration());
				disp.setTransformationInterpolationDelay(data.getStartInterpolation());
				display.applyTransformation(data.getTransformation(), world);
			}
		}
		updateOffsets();
	}

	private void updatePositions() {
		if (level() instanceof ServerLevel) {
			for (var d : riderSlots) {
				d.updatePos(this, (ServerLevel) level());
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
	public void absSnapRotationTo(float yaw, float pitch) {
		super.absSnapRotationTo(yaw, pitch);
		updateOffsets();
	}

	@Override
	public void setOldRot() {
		if (yRotO != getYRot() || xRotO != getXRot()) {
			updateOffsets();
		}
		super.setOldRot();
	}

	@Override
	public void tick() {
		super.tick();
		if (yRotO != getYRot() || xRotO != getXRot()) {
			updateOffsets();
		}
		if (xo != getX() || yo != getY() || zo != getZ()) {
			updatePositions();
		}
	}

	@Override
	public boolean hurtServer(ServerLevel world, DamageSource source, float amount) {
		return false;
	}

	private void initAttachment() {
		EntityAttachment.ofTicking(holder, this);
	}

	@Override
	public EntityType<?> getPolymerEntityType(PacketContext ctx) {
		return EntityType.MARKER;
	}

	public static Vec3 applyOffset(
			StructureDisplay entity, Vec3 offset
	) {
		var mat = new Matrix4f();
		mat.rotateYXZ(
				-entity.getYRot() * Mth.DEG_TO_RAD,
				entity.getXRot() * Mth.DEG_TO_RAD,
				0
		);
		Vector3d newOffset = new Vector3d(offset.x, offset.y, offset.z);
		var transformation = new Transformation(
				null,
				entity.data.getTransformation().getLeftRotation(),
				entity.data.getTransformation().getScale(),
				entity.data.getTransformation().getRightRotation()
		);
		mat.mul(transformation.getMatrix());
		newOffset.mulPosition(mat);
		return new Vec3(newOffset.x, newOffset.y, newOffset.z);
	}

	public static Matrix4f applyTransformation(
			float yawOffset, float pitchOffset,
			Transformation transformation
	) {
		var rotated = transformation.getMatrixCopy();
		if (yawOffset != 0 || pitchOffset != 0) {
			var rot = new Matrix4f().rotateXYZ(
					-pitchOffset * Mth.DEG_TO_RAD,
					-yawOffset * Mth.DEG_TO_RAD,
					0
			);
			rotated = rotated.mul(rot);
		}
		return rotated;
	}

	public record Display(DisplayElement displayElement, Vec3 offset, float yawOffset, float pitchOffset) {
		public void updateOffset(StructureDisplay entity) {
			displayElement.setOffset(
					StructureDisplay.applyOffset(entity, offset)
			);
			displayElement.setYaw(entity.getYRot());
			displayElement.setPitch(entity.getXRot());
		}

		public void applyTransformation(Transformation transformation) {
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
						UUIDUtil.AUTHLIB_CODEC.fieldOf("entity").forGetter(t -> t.entity),
						Vec3.CODEC.fieldOf("offset").forGetter(t -> t.offset),
						Codec.FLOAT.fieldOf("yaw_offset").forGetter(t -> t.yawOffset),
						Codec.FLOAT.fieldOf("pitch_offset").forGetter(t -> t.pitchOffset)
				).apply(instance, DisplayRider::new)
		);

		public static final Codec<List<DisplayRider>> LIST_CODEC = CODEC.listOf();

		public final UUID entity;
		public final Vec3 offset;
		public final float yawOffset;
		public final float pitchOffset;

		@Nullable
		private net.minecraft.world.entity.Display disp;

		public DisplayRider(UUID entity, Vec3 offset, float yawOffset, float pitchOffset) {
			this.entity = entity;
			this.offset = offset;
			this.yawOffset = yawOffset;
			this.pitchOffset = pitchOffset;
		}

		@Nullable
		public net.minecraft.world.entity.Display getDisplay(ServerLevel world) {
			if (disp == null || !disp.isAlive()) {
				var e = world.getEntity(entity);
				if (e instanceof net.minecraft.world.entity.Display d) {
					this.disp = d;
				}
			}
			return disp;
		}

		public void updatePos(StructureDisplay entity, ServerLevel world) {
			var offset = StructureDisplay.applyOffset(entity, this.offset);
			var display = getDisplay(world);
			if (display == null) return;
			var prevYaw = display.getYRot();
			var prevPitch = display.getXRot();
			display.absSnapTo(
					entity.getX() + offset.x(),
					entity.getY() + offset.y(),
					entity.getZ() + offset.z(),
					entity.getYRot(),
					entity.getXRot()
			);
			var yawDiff = entity.getYRot() - prevYaw;
			var pitchDiff = entity.getXRot() - prevPitch;
			display.getIndirectPassengers().forEach(p -> {
				p.setYRot(p.getYRot() + yawDiff);
				p.setYBodyRot(p.getVisualRotationYInDegrees() + yawDiff);
				p.setYHeadRot(p.getYHeadRot() + yawDiff);
				p.setXRot(p.getXRot() + pitchDiff);
			});
		}

		public void applyTransformation(
				Transformation transformation, ServerLevel world
		) {
			var disp = getDisplay(world);
			if (disp == null) return;
			disp.setTransformation(
					new Transformation(
							StructureDisplay.applyTransformation(
									yawOffset, pitchOffset, transformation
							)
					)
			);
		}
	}

}
