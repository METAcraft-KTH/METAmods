package nu.metacraft.faster_minecarts.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import nu.metacraft.faster_minecarts.FasterMinecartsConfig;
import nu.metacraft.faster_minecarts.MinecartExtensions;

import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundMoveMinecartPacket;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.MinecartBehavior;
import net.minecraft.world.entity.vehicle.NewMinecartBehavior;
import net.minecraft.world.entity.vehicle.OldMinecartBehavior;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.properties.RailShape;

@Mixin(ServerEntity.class)
public abstract class MixinEntityTrackerEntry {

	@Shadow @Final private Entity entity;

	@Shadow private int tickCount;

	@Shadow @Final private int updateInterval;

	@Shadow protected abstract void sendDirtyEntityData();

	@Shadow @Final private ServerEntity.Synchronizer synchronizer;

	@ModifyExpressionValue(
		method = "sendChanges",
		at = @At(
				value = "INVOKE",
				target = "Lnet/minecraft/world/entity/vehicle/AbstractMinecart;getBehavior()Lnet/minecraft/world/entity/vehicle/MinecartBehavior;"
		)
	)
	public MinecartBehavior makeControllerNull(MinecartBehavior controller) {
		if (
				FasterMinecartsConfig.getConfig().experimentalMinecartMode().isEnabled() &&
				controller instanceof OldMinecartBehavior
		) {
			return null;
		}
		return controller;
	}

	@WrapOperation(
		method = "sendChanges",
		constant = @Constant(
				classValue = NewMinecartBehavior.class,
				ordinal = 0
		) //Targets instanceof ExperimentalMinecartController
	)
	public boolean simulateLegacyCartMovement(Object controller, Operation<Boolean> original, @Local AbstractMinecart minecart) {
		if (controller == null) {
			sendDirtyEntityData();
			var railPos = minecart.getCurrentBlockPosOrRailBelow();
			var railState = entity.level().getBlockState(railPos);
			float yaw = this.entity.getYRot();
			float pitch = this.entity.getXRot();
			var data = (MinecartExtensions) minecart;
			double xDist = minecart.xo - minecart.getX();
			double zDist = minecart.zo - minecart.getZ();
			boolean yawUpdate = xDist * xDist + zDist * zDist > 0.001;
			if (railState.getBlock() instanceof BaseRailBlock railBlock) {
				var railShape = railState.getValue(railBlock.getShapeProperty());
				if (railShape.isSlope()) {
					var facing = entity.getDirection();

					Direction rail = switch (railShape) {
						case ASCENDING_NORTH -> Direction.NORTH;
						case ASCENDING_SOUTH -> Direction.SOUTH;
						case ASCENDING_EAST -> Direction.EAST;
						case ASCENDING_WEST -> Direction.WEST;
						default -> null;
					};

					if (rail != null) {
						int pitchOffset;

						if (rail.getClockWise() == facing) {
							pitchOffset = -45;
						} else {
							pitchOffset = 45;
						}

						pitch += pitchOffset;

						if (!data.fasterMinecarts$yawFixed() && rail != Direction.EAST) {
							yaw = Direction.getYRot(rail.getClockWise());
							pitch = -pitch;
						}
					}
				}
				if (railShape == RailShape.NORTH_SOUTH && !data.fasterMinecarts$yawFixed()) {
					yaw = -90 * data.fasterMinecarts$getInitialZ().getStep();
				}
				if (entity.getDeltaMovement().horizontalDistanceSqr() < 1.0E-7) {
					switch (railShape) {
						case NORTH_EAST, SOUTH_EAST, NORTH_WEST, SOUTH_WEST -> yaw = -yaw;
					}
				}
			}
			if (this.entity.getDeltaMovement().horizontalDistanceSqr() > 1.0E-7 || yawUpdate || this.tickCount % this.updateInterval == 0) {
				this.synchronizer.sendToTrackingPlayers(
						new ClientboundMoveMinecartPacket(
								this.entity.getId(),
								List.of(
										new NewMinecartBehavior.MinecartStep(
												this.entity.position(), this.entity.getDeltaMovement(),
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
		method = "sendChanges",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/level/ServerEntity;handleMinecartPosRot(Lnet/minecraft/world/entity/vehicle/NewMinecartBehavior;BBZ)V"
		)
	)
	public boolean skip(ServerEntity instance, NewMinecartBehavior controller, byte yaw, byte pitch, boolean changedAngles) {
		return controller != null;
	}

}
