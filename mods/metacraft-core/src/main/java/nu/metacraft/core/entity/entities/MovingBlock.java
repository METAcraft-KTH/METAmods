package nu.metacraft.core.entity.entities;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import eu.pb4.polymer.core.api.entity.PolymerEntity;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import eu.pb4.polymer.virtualentity.api.elements.BlockDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.EntityElement;
import eu.pb4.polymer.virtualentity.api.tracker.DisplayTrackedData;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.Flutterer;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.mob.ShulkerEntity;
import net.minecraft.entity.player.PlayerPosition;
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import nu.metacraft.core.extensions.EntityExtensions;
import nu.metacraft.core.mixin.AccessorEntity;
import nu.metacraft.core.mixin.AccessorLivingEntity;
import nu.metacraft.core.util.DisplayEntityData;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.*;

public class MovingBlock extends Entity implements PolymerEntity {

	public static final String SLIPPERINESS = "slipperiness";
	public static final String ANCHOR = "anchor";

	public static final double MAX_MOVE_DIST = 2;
	public static final double SQ_MAX_MOVE_DIST = MAX_MOVE_DIST * MAX_MOVE_DIST;

	private final ElementHolder holder = new ElementHolder();
	private final BlockDisplayElement block;
	private final DisplayEntityData.Block blockData = new DisplayEntityData.Block();
	private Optional<Float> slipperiness = Optional.empty();
	private final EntityElement<ShulkerEntity> shulker;

	private Anchor anchor = null;
	private AnchorEntity anchorEntity = null;

	private boolean initializedShulker = false;

	public MovingBlock(EntityType<?> type, World world) {
		super(type, world);
		block = new BlockDisplayElement(Blocks.STONE.getDefaultState());
		block.setTransformation(new AffineTransformation(
				new Vector3f(-0.5f, 0, -0.5f),
				new Quaternionf(),
				new Vector3f(1, 1, 1),
				new Quaternionf()
		));
		block.setTeleportDuration(1);
		holder.addElement(block);
		if (world instanceof ServerWorld sw) {
			shulker = new EntityElement<>(EntityType.SHULKER, sw);
			shulker.entity().setInvisible(true);
			shulker.setInitialPosition(this.getPos());
			holder.addPassengerElement(shulker);
		} else {
			shulker = null;
		}
		EntityAttachment.ofTicking(holder, this);
	}

	@Override
	public Entity teleportTo(TeleportTarget teleportTarget) {
		var teleported = super.teleportTo(teleportTarget);
		if (teleported != null) {
			var box = selectionBox(this.getBoundingBox(), Vec3d.ZERO);
			for (var entity : getWorld().getOtherEntities(this, box, this::shouldMove)) {
				entity.teleportTo(teleportTarget.withPosition(
						entity.getPos().subtract(this.getPos()).add(teleported.getPos())
				));
			}
		}
		return teleported;
	}

	@Override
	public void tick() {
		super.tick();

		if (getWorld() instanceof ServerWorld) {
			this.move(MovementType.SELF, this.getVelocity());
			if (!initializedShulker) { //Shulker is usually not present when it first spawns, shows up after first movement.
				shulker.entity().updatePosition(this.getX(), this.getY(), this.getZ());
				initializedShulker = true;
			}
		}

	}

	private static float getMovement(boolean positive, boolean negative) {
		if (positive == negative) {
			return 0;
		} else {
			return positive ? 1 : -1;
		}
	}

	private void movePlayer(ServerPlayerEntity player, Vec3d movement) {
		var root = player.getRootVehicle();
		float forwardMovement = getMovement(player.getPlayerInput().forward(), player.getPlayerInput().backward());
		float sidewaysMovement = getMovement(player.getPlayerInput().left(), player.getPlayerInput().right());

		if (player.isUsingItem() && !player.hasVehicle()) {
			forwardMovement *= 0.2F;
			sidewaysMovement *= 0.2F;
		}

		if (player.isInSneakingPose() || player.isCrawling()) {
			float f = (float) player.getAttributeValue(EntityAttributes.SNEAKING_SPEED);
			forwardMovement *= f;
			sidewaysMovement *= f;
		}

		var slipperiness = this.slipperiness.orElse(blockData.getBlockState().getBlock().getSlipperiness());

		float g = slipperiness * 0.91F;
		float h = root instanceof Flutterer ? g : 0.98F;
		root.setVelocity(root.getVelocity().x * (double)g, root.getVelocity().y * (double)h, root.getVelocity().z * (double)g);

		var velocity = movementInputToVelocity(
				new Vec3d(sidewaysMovement, 0, forwardMovement),
				player.getMovementSpeed() * (0.21600002F / (slipperiness * slipperiness * slipperiness)),
				player.getYaw()
		);
		root.setVelocity(root.getVelocity().add(velocity));

		if (player.getPlayerInput().jump()) {
			float jumpStrength = ((AccessorLivingEntity) root).callGetJumpVelocity();
			if (jumpStrength > 1.0E-5F) {
				Vec3d currentVelocity = root.getVelocity();
				root.setVelocity(currentVelocity.x, Math.max(jumpStrength, currentVelocity.y) + 0.1, currentVelocity.z);
				if (player.getPlayerInput().sprint()) {
					float rot = root.getYaw() * ((float)Math.PI / 180F);
					root.addVelocityInternal(new Vec3d((-MathHelper.sin(rot)) * 0.2, 0, MathHelper.cos(rot) * 0.2));
				}
			}
		}


		Vec3d motionVec = movement.add(root.getVelocity());
		motionVec = ((AccessorEntity) root).callAdjustMovementForSneaking(motionVec, MovementType.SELF);
		motionVec = ((AccessorEntity) root).callAdjustMovementForCollisions(motionVec);

		boolean removeY = false;
		if (movement.getY() > 0 && root.getVelocity().y <= 0) {
			motionVec = new Vec3d(
					motionVec.getX(),
					this.getBoundingBox().maxY,
					motionVec.getZ()
			);
			removeY = true;
		}

		var targetPos = new PlayerPosition(motionVec, root.getVelocity(), 0, 0);
		var set = EnumSet.allOf(PositionFlag.class);
		set.removeAll(PositionFlag.DELTA);
		if (removeY) {
			set.remove(PositionFlag.Y);
		}
		root.setOnGround(false);
		root.setPosition(targetPos, set);
		player.networkHandler.sendPacket(
				EntityPositionS2CPacket.create(
						root.getId(), targetPos, set, false
				)
		);
	}

	public boolean shouldMove(Entity entity) {
		return !this.isConnectedThroughVehicle(entity) && !entity.noClip && !(entity instanceof MovingBlock) && ((EntityExtensions) entity).metacraft$getLastMovedByMovingBlockTick() != getWorld().getTime();
	}

	private ServerPlayerEntity getRelevantPlayer(Entity entity) {
		if (entity instanceof ServerPlayerEntity p) {
			return p;
		}
		if (entity.getControllingPassenger() instanceof ServerPlayerEntity p) {
			return p;
		}
		return null;
	}

	private void moveEntity(Entity entity, Vec3d movement) {
		if (entity.hasVehicle()) return;
		var player = getRelevantPlayer(entity);
		if (player != null) {
			movePlayer(player, movement);
		} else {
			entity.move(MovementType.SHULKER, movement);
		}
		((EntityExtensions) entity).metacraft$setLastMovedByMovingBlockTick(getWorld().getTime());
	}

	private static Box selectionBox(Box entityBox, Vec3d movement) {
		return entityBox.stretch(movement).expand(0.05);
	}

	private static boolean isMovementValid(Vec3d movement) {
		return movement.length() < MAX_MOVE_DIST;
	}

	@Override
	public void move(MovementType type, Vec3d movement) {
		var entityBox = this.getBoundingBox();
		super.move(type, movement);
		if (isMovementValid(movement)) {
			var box = selectionBox(entityBox, movement);
			for (var entity : getWorld().getOtherEntities(this, box, this::shouldMove)) {
				moveEntity(entity, movement);
			}
		}
	}

	@Override
	public void modifyRawTrackedData(List<DataTracker.SerializedEntry<?>> data, ServerPlayerEntity player, boolean initial) {
		data.add(
				DataTracker.SerializedEntry.of(
						DisplayTrackedData.TELEPORTATION_DURATION, 1
				)
		);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {

	}

	@Override
	public boolean damage(ServerWorld world, DamageSource source, float amount) {
		return false;
	}

	@Override
	protected void readCustomData(ReadView nbt) {
		nbt.read(ANCHOR, Anchor.CODEC).ifPresentOrElse(
				this::setRootAnchor,
				() -> setRootAnchor(null)
		);
		blockData.load(nbt, this);
		blockData.applySettings(block);
		blockData.applyBlockSettings(block);
		slipperiness = nbt.read(SLIPPERINESS, Codec.FLOAT);
	}

	@Override
	protected void writeCustomData(WriteView nbt) {
		if (anchor != null) {
			nbt.put(ANCHOR, Anchor.CODEC, anchor);
		}
		blockData.save(nbt, this);
		slipperiness.ifPresent(
				s -> nbt.putFloat(SLIPPERINESS, s)
		);
	}

	public void setRootAnchor(Anchor anchor) {
		this.anchor = anchor;
		anchorEntity = null;
	}

	public Optional<AnchorEntity> getRootAnchor() {
		if (anchorEntity != null) return Optional.of(anchorEntity);
		if (anchor == null) return Optional.empty();
		if (getWorld() instanceof ServerWorld world) {
			var offset = anchor.offset;
			var e = world.getEntity(this.anchor.id);
			while (e instanceof MovingBlock b && b.anchor != null) {
				e = world.getEntity(b.anchor.id);
				offset = offset.add(b.anchor.offset);
				if (e == this) return Optional.empty();
			}
			anchorEntity = e != null ? new AnchorEntity(e, offset) : null;
			return Optional.ofNullable(anchorEntity);
		}
		return Optional.empty();
	}

	@Override
	public boolean isCollidable(@Nullable Entity entity) {
		return true;
	}

	@Override
	public boolean collidesWith(Entity other) {
		var rootAnchor = this.getRootAnchor();
		if (rootAnchor.isEmpty() || (other instanceof MovingBlock b && AnchorEntity.matches(rootAnchor, b.getRootAnchor()))) {
			return false;
		} else {
			return super.collidesWith(other);
		}
	}

	@Override
	public EntityType<?> getPolymerEntityType(PacketContext packetContext) {
		return EntityType.ITEM_DISPLAY;
	}

	public record Anchor(UUID id, Vec3d offset) {
		public static final Codec<Anchor> CODEC = Codec.withAlternative(
				RecordCodecBuilder.create(
						instance -> instance.group(
								Uuids.STRICT_CODEC.fieldOf("id").forGetter(Anchor::id),
								Vec3d.CODEC.optionalFieldOf("offset", Vec3d.ZERO).forGetter(Anchor::offset)
						).apply(instance, Anchor::new)
				),
				Uuids.STRICT_CODEC,
				id -> new Anchor(id, Vec3d.ZERO)
		);
	}

	public record AnchorEntity(Entity entity, Vec3d offset) {
		public boolean matches(AnchorEntity other) {
			return entity.equals(other.entity);
		}

		public static boolean matches(Optional<AnchorEntity> lhs, Optional<AnchorEntity> rhs) {
			if (lhs.isPresent() && rhs.isPresent()) {
				return lhs.get().matches(rhs.get());
			}
			return false;
		}

		public Vec3d getTargetPos() {
			return entity.getPos().subtract(offset);
		}
	}
}
