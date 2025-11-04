package nu.metacraft.core.entity.entities;

import com.mojang.math.Transformation;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import eu.pb4.polymer.core.api.entity.PolymerEntity;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import eu.pb4.polymer.virtualentity.api.elements.BlockDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.EntityElement;
import eu.pb4.polymer.virtualentity.api.tracker.DisplayTrackedData;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import nu.metacraft.core.extensions.EntityExtensions;
import nu.metacraft.core.mixin.AccessorEntity;
import nu.metacraft.core.mixin.AccessorLivingEntity;
import nu.metacraft.core.util.DisplayEntityData;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.*;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class MovingBlock extends Entity implements PolymerEntity {

	public static final String SLIPPERINESS = "slipperiness";
	public static final String ANCHOR = "anchor";

	public static final double MAX_MOVE_DIST = 2;
	public static final double SQ_MAX_MOVE_DIST = MAX_MOVE_DIST * MAX_MOVE_DIST;

	private final ElementHolder holder = new ElementHolder();
	private final BlockDisplayElement block;
	private final DisplayEntityData.Block blockData = new DisplayEntityData.Block();
	private Optional<Float> slipperiness = Optional.empty();
	private final EntityElement<Shulker> shulker;

	private Anchor anchor = null;
	private AnchorEntity anchorEntity = null;

	private boolean initializedShulker = false;

	public MovingBlock(EntityType<?> type, Level world) {
		super(type, world);
		block = new BlockDisplayElement(Blocks.STONE.defaultBlockState());
		block.setTransformation(new Transformation(
				new Vector3f(-0.5f, 0, -0.5f),
				new Quaternionf(),
				new Vector3f(1, 1, 1),
				new Quaternionf()
		));
		block.setTeleportDuration(1);
		holder.addElement(block);
		if (world instanceof ServerLevel sw) {
			shulker = new EntityElement<>(EntityType.SHULKER, sw);
			shulker.entity().setInvisible(true);
			shulker.setInitialPosition(this.position());
			holder.addPassengerElement(shulker);
		} else {
			shulker = null;
		}
		EntityAttachment.ofTicking(holder, this);
	}

	@Override
	public Entity teleport(TeleportTransition teleportTarget) {
		var teleported = super.teleport(teleportTarget);
		if (teleported != null) {
			var box = selectionBox(this.getBoundingBox(), Vec3.ZERO);
			for (var entity : level().getEntities(this, box, this::shouldMove)) {
				entity.teleport(teleportTarget.withPosition(
						entity.position().subtract(this.position()).add(teleported.position())
				));
			}
		}
		return teleported;
	}

	@Override
	public void tick() {
		super.tick();

		if (level() instanceof ServerLevel) {
			this.move(MoverType.SELF, this.getDeltaMovement());
			if (!initializedShulker) { //Shulker is usually not present when it first spawns, shows up after first movement.
				shulker.entity().absSnapTo(this.getX(), this.getY(), this.getZ());
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

	private void movePlayer(ServerPlayer player, Vec3 movement) {
		var root = player.getRootVehicle();
		float forwardMovement = getMovement(player.getLastClientInput().forward(), player.getLastClientInput().backward());
		float sidewaysMovement = getMovement(player.getLastClientInput().left(), player.getLastClientInput().right());

		if (player.isUsingItem() && !player.isPassenger()) {
			forwardMovement *= 0.2F;
			sidewaysMovement *= 0.2F;
		}

		if (player.isCrouching() || player.isVisuallyCrawling()) {
			float f = (float) player.getAttributeValue(Attributes.SNEAKING_SPEED);
			forwardMovement *= f;
			sidewaysMovement *= f;
		}

		var slipperiness = this.slipperiness.orElse(blockData.getBlockState().getBlock().getFriction());

		float g = slipperiness * 0.91F;
		float h = root instanceof FlyingAnimal ? g : 0.98F;
		root.setDeltaMovement(root.getDeltaMovement().x * (double)g, root.getDeltaMovement().y * (double)h, root.getDeltaMovement().z * (double)g);

		var velocity = getInputVector(
				new Vec3(sidewaysMovement, 0, forwardMovement),
				player.getSpeed() * (0.21600002F / (slipperiness * slipperiness * slipperiness)),
				player.getYRot()
		);
		root.setDeltaMovement(root.getDeltaMovement().add(velocity));

		if (player.getLastClientInput().jump()) {
			float jumpStrength = ((AccessorLivingEntity) root).callGetJumpPower();
			if (jumpStrength > 1.0E-5F) {
				Vec3 currentVelocity = root.getDeltaMovement();
				root.setDeltaMovement(currentVelocity.x, Math.max(jumpStrength, currentVelocity.y) + 0.1, currentVelocity.z);
				if (player.getLastClientInput().sprint()) {
					float rot = root.getYRot() * ((float)Math.PI / 180F);
					root.addDeltaMovement(new Vec3((-Mth.sin(rot)) * 0.2, 0, Mth.cos(rot) * 0.2));
				}
			}
		}


		Vec3 motionVec = movement.add(root.getDeltaMovement());
		motionVec = ((AccessorEntity) root).callMaybeBackOffFromEdge(motionVec, MoverType.SELF);
		motionVec = ((AccessorEntity) root).callCollide(motionVec);

		boolean removeY = false;
		if (movement.y() > 0 && root.getDeltaMovement().y <= 0) {
			motionVec = new Vec3(
					motionVec.x(),
					this.getBoundingBox().maxY,
					motionVec.z()
			);
			removeY = true;
		}

		var targetPos = new PositionMoveRotation(motionVec, root.getDeltaMovement(), 0, 0);
		var set = EnumSet.allOf(Relative.class);
		set.removeAll(Relative.DELTA);
		if (removeY) {
			set.remove(Relative.Y);
		}
		root.setOnGround(false);
		root.teleportSetPosition(targetPos, set);
		player.connection.send(
				ClientboundTeleportEntityPacket.teleport(
						root.getId(), targetPos, set, false
				)
		);
	}

	public boolean shouldMove(Entity entity) {
		return !this.isPassengerOfSameVehicle(entity) && !entity.noPhysics && !(entity instanceof MovingBlock) && ((EntityExtensions) entity).metacraft$getLastMovedByMovingBlockTick() != level().getGameTime();
	}

	private ServerPlayer getRelevantPlayer(Entity entity) {
		if (entity instanceof ServerPlayer p) {
			return p;
		}
		if (entity.getControllingPassenger() instanceof ServerPlayer p) {
			return p;
		}
		return null;
	}

	private void moveEntity(Entity entity, Vec3 movement) {
		if (entity.isPassenger()) return;
		var player = getRelevantPlayer(entity);
		if (player != null) {
			movePlayer(player, movement);
		} else {
			entity.move(MoverType.SHULKER, movement);
		}
		((EntityExtensions) entity).metacraft$setLastMovedByMovingBlockTick(level().getGameTime());
	}

	private static AABB selectionBox(AABB entityBox, Vec3 movement) {
		return entityBox.expandTowards(movement).inflate(0.05);
	}

	private static boolean isMovementValid(Vec3 movement) {
		return movement.length() < MAX_MOVE_DIST;
	}

	@Override
	public void move(MoverType type, Vec3 movement) {
		var entityBox = this.getBoundingBox();
		super.move(type, movement);
		if (isMovementValid(movement)) {
			var box = selectionBox(entityBox, movement);
			for (var entity : level().getEntities(this, box, this::shouldMove)) {
				moveEntity(entity, movement);
			}
		}
	}

	@Override
	public void modifyRawTrackedData(List<SynchedEntityData.DataValue<?>> data, ServerPlayer player, boolean initial) {
		data.add(
				SynchedEntityData.DataValue.create(
						DisplayTrackedData.TELEPORTATION_DURATION, 1
				)
		);
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
	protected void addAdditionalSaveData(ValueOutput nbt) {
		if (anchor != null) {
			nbt.store(ANCHOR, Anchor.CODEC, anchor);
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
		if (level() instanceof ServerLevel world) {
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
	public boolean canBeCollidedWith(@Nullable Entity entity) {
		return true;
	}

	@Override
	public boolean canCollideWith(Entity other) {
		var rootAnchor = this.getRootAnchor();
		if (rootAnchor.isEmpty() || (other instanceof MovingBlock b && AnchorEntity.matches(rootAnchor, b.getRootAnchor()))) {
			return false;
		} else {
			return super.canCollideWith(other);
		}
	}

	@Override
	public EntityType<?> getPolymerEntityType(PacketContext packetContext) {
		return EntityType.ITEM_DISPLAY;
	}

	public record Anchor(UUID id, Vec3 offset) {
		public static final Codec<Anchor> CODEC = Codec.withAlternative(
				RecordCodecBuilder.create(
						instance -> instance.group(
								UUIDUtil.LENIENT_CODEC.fieldOf("id").forGetter(Anchor::id),
								Vec3.CODEC.optionalFieldOf("offset", Vec3.ZERO).forGetter(Anchor::offset)
						).apply(instance, Anchor::new)
				),
				UUIDUtil.LENIENT_CODEC,
				id -> new Anchor(id, Vec3.ZERO)
		);
	}

	public record AnchorEntity(Entity entity, Vec3 offset) {
		public boolean matches(AnchorEntity other) {
			return entity.equals(other.entity);
		}

		public static boolean matches(Optional<AnchorEntity> lhs, Optional<AnchorEntity> rhs) {
			if (lhs.isPresent() && rhs.isPresent()) {
				return lhs.get().matches(rhs.get());
			}
			return false;
		}

		public Vec3 getTargetPos() {
			return entity.position().subtract(offset);
		}
	}
}
