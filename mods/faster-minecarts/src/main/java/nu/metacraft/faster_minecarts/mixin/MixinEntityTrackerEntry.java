package nu.metacraft.faster_minecarts.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.AbstractRailBlock;
import net.minecraft.block.enums.RailShape;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.entity.vehicle.DefaultMinecartController;
import net.minecraft.entity.vehicle.ExperimentalMinecartController;
import net.minecraft.entity.vehicle.MinecartController;
import net.minecraft.network.packet.s2c.play.MoveMinecartAlongTrackS2CPacket;
import net.minecraft.server.network.EntityTrackerEntry;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import nu.metacraft.faster_minecarts.FasterMinecartsConfig;
import nu.metacraft.faster_minecarts.MinecartExtensions;

import java.util.List;

@Mixin(EntityTrackerEntry.class)
public abstract class MixinEntityTrackerEntry {

	@Shadow @Final private Entity entity;

	@Shadow private int trackingTick;

	@Shadow @Final private int tickInterval;

	@Shadow protected abstract void syncEntityData();

	@Shadow @Final private EntityTrackerEntry.TrackerPacketSender packetSender;

	@ModifyExpressionValue(
		method = "tick",
		at = @At(
				value = "INVOKE",
				target = "Lnet/minecraft/entity/vehicle/AbstractMinecartEntity;getController()Lnet/minecraft/entity/vehicle/MinecartController;"
		)
	)
	public MinecartController makeControllerNull(MinecartController controller) {
		if (
				FasterMinecartsConfig.getConfig().experimentalMinecartMode().isEnabled() &&
				controller instanceof DefaultMinecartController
		) {
			return null;
		}
		return controller;
	}

	@WrapOperation(
		method = "tick",
		constant = @Constant(
				classValue = ExperimentalMinecartController.class,
				ordinal = 0
		) //Targets instanceof ExperimentalMinecartController
	)
	public boolean simulateLegacyCartMovement(Object controller, Operation<Boolean> original, @Local AbstractMinecartEntity minecart) {
		if (controller == null) {
			syncEntityData();
			var railPos = minecart.getRailOrMinecartPos();
			var railState = entity.getEntityWorld().getBlockState(railPos);
			float yaw = this.entity.getYaw();
			float pitch = this.entity.getPitch();
			var data = (MinecartExtensions) minecart;
			double xDist = minecart.lastX - minecart.getX();
			double zDist = minecart.lastZ - minecart.getZ();
			boolean yawUpdate = xDist * xDist + zDist * zDist > 0.001;
			if (railState.getBlock() instanceof AbstractRailBlock railBlock) {
				var railShape = railState.get(railBlock.getShapeProperty());
				if (railShape.isAscending()) {
					var facing = entity.getHorizontalFacing();

					Direction rail = switch (railShape) {
						case ASCENDING_NORTH -> Direction.NORTH;
						case ASCENDING_SOUTH -> Direction.SOUTH;
						case ASCENDING_EAST -> Direction.EAST;
						case ASCENDING_WEST -> Direction.WEST;
						default -> null;
					};

					if (rail != null) {
						int pitchOffset;

						if (rail.rotateYClockwise() == facing) {
							pitchOffset = -45;
						} else {
							pitchOffset = 45;
						}

						pitch += pitchOffset;

						if (!data.fasterMinecarts$yawFixed() && rail != Direction.EAST) {
							yaw = Direction.getHorizontalDegreesOrThrow(rail.rotateYClockwise());
							pitch = -pitch;
						}
					}
				}
				if (railShape == RailShape.NORTH_SOUTH && !data.fasterMinecarts$yawFixed()) {
					yaw = -90 * data.fasterMinecarts$getInitialZ().offset();
				}
				if (entity.getVelocity().horizontalLengthSquared() < 1.0E-7) {
					switch (railShape) {
						case NORTH_EAST, SOUTH_EAST, NORTH_WEST, SOUTH_WEST -> yaw = -yaw;
					}
				}
			}
			if (this.entity.getVelocity().horizontalLengthSquared() > 1.0E-7 || yawUpdate || this.trackingTick % this.tickInterval == 0) {
				this.packetSender.sendToListeners(
						new MoveMinecartAlongTrackS2CPacket(
								this.entity.getId(),
								List.of(
										new ExperimentalMinecartController.Step(
												this.entity.getPos(), this.entity.getVelocity(),
												-yaw, pitch,
												1.0f
										)
								)
						)
				);
			}
			return true;
		}
		return original.call(controller);
	}

	@WrapWithCondition(
		method = "tick",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/network/EntityTrackerEntry;tickExperimentalMinecart(Lnet/minecraft/entity/vehicle/ExperimentalMinecartController;BBZ)V"
		)
	)
	public boolean skip(EntityTrackerEntry instance, ExperimentalMinecartController controller, byte yaw, byte pitch, boolean changedAngles) {
		return controller != null;
	}

}
