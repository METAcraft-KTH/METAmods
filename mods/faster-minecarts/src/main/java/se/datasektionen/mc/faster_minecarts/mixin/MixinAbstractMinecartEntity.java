package se.datasektionen.mc.faster_minecarts.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import net.minecraft.block.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.datasektionen.mc.faster_minecarts.FasterMinecarts;
import se.datasektionen.mc.faster_minecarts.FasterMinecartsConfig;
import se.datasektionen.mc.faster_minecarts.MinecartData;
import se.datasektionen.mc.faster_minecarts.configs.EntityFactorConfig;

import java.util.Optional;
import java.util.OptionalDouble;

@Mixin(AbstractMinecartEntity.class)
public abstract class MixinAbstractMinecartEntity extends Entity implements MinecartData {

	@Unique
	private OptionalDouble acceleration = OptionalDouble.empty();

	@Unique
	private OptionalDouble maxSpeed = OptionalDouble.empty();

	@Unique
	private OptionalDouble maxSpeedUnderwater = OptionalDouble.empty();

	@Unique
	private Optional<String> craftingTag = Optional.empty();

	@Unique
	private Optional<Text> itemName = Optional.empty();

	@Unique
	private boolean hasSpeedUpgrade = false;

	@Shadow protected abstract double getMaxSpeed();

	@Shadow public abstract void onActivatorRail(int x, int y, int z, boolean powered);

	@Shadow protected abstract void applySlowdown();

	public MixinAbstractMinecartEntity(EntityType<?> entityType, World world) {
		super(entityType, world);
	}

	@ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/BlockState;isOf(Lnet/minecraft/block/Block;)Z"))
	public boolean activatorRail(
			boolean isOfBlock, @Share("shouldUseActivatorRail") LocalBooleanRef shouldUseActivatorRail
	) {
		if (shouldUseActivatorRail.get()) {
			return isOfBlock;
		} else {
			return false;
		}
	}

	@Unique
	private boolean hasSuperSpeed() {
		return hasSpeedUpgrade || FasterMinecartsConfig.getConfig().globalFasterMinecarts;
	}

	@ModifyExpressionValue(
		method = "moveOnRail",
		at = @At(
			value = "CONSTANT",
			args = "doubleValue=0.06"
		)
	)
	public double changePoweredRailAcceleration(double acceleration) {
		return this.acceleration.orElse(getValue(acceleration, FasterMinecartsConfig.getPoweredRailAccelerationFactor()));
	}

	@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/vehicle/AbstractMinecartEntity;moveOnRail(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;)V"))
	public void moveMinecartsSeveralTimesPerTick(
			AbstractMinecartEntity instance, BlockPos pos, BlockState state, Operation<Void> moveOnRail,
			@Share("shouldUseActivatorRail") LocalBooleanRef shouldUseActivatorRail
	) {
		shouldUseActivatorRail.set(true);
		final double maxSpeed = 0.4;
		if (this.getVelocity().horizontalLength() > maxSpeed && hasSuperSpeed()) {
			shouldUseActivatorRail.set(false);
			double prevVelocity = this.getVelocity().horizontalLength();

			Vec3d currentVelocity = this.getVelocity().normalize().multiply(maxSpeed);

			double distanceMoved = 0;

			while (prevVelocity > distanceMoved) {
				Vec3d prevPos = this.getPos();
				if (currentVelocity.horizontalLength() < 0.01) {
					break;
				}
				this.setVelocity(currentVelocity);
				BlockPos railPos = this.getBlockPos();
				BlockState railState = this.getWorld().getBlockState(railPos);
				if (!(railState.getBlock() instanceof AbstractRailBlock)) {
					railPos = railPos.up();
					railState = this.getWorld().getBlockState(railPos);
					if (!(railState.getBlock() instanceof AbstractRailBlock)) {
						railPos = railPos.down(2);
						railState = this.getWorld().getBlockState(railPos);
						if (!(railState.getBlock() instanceof AbstractRailBlock)) {
							this.setVelocity(this.getVelocity().normalize().multiply(prevVelocity));
							break;
						}
					}
				}

				Vec3d facing = currentVelocity.normalize();
				Vec3d left = facing.rotateY((float) Math.PI / 2);
				double halfWidth = this.getWidth()/2;
				Vec3d boxStart = this.getPos().add(facing.multiply(halfWidth));
				Box ahead = new Box(boxStart.add(left.multiply(-halfWidth)), boxStart.add(facing.multiply(currentVelocity.horizontalLength())).add(left.multiply(halfWidth)).add(0, this.getHeight(),0));
				FasterMinecarts.damageEntitiesFromCart(this, prevVelocity, ahead);

				moveOnRail.call(instance, railPos, railState);

				double newSpeed = this.getVelocity().horizontalLength();
				if (newSpeed == 0) {
					break;
				}

				double distance = this.getPos().distanceTo(prevPos);

				double acceleration = (Math.pow(newSpeed, 2) - Math.pow(currentVelocity.horizontalLength(), 2)) / (2 * distance);
				double time = distance / prevVelocity;
				prevVelocity += acceleration * time;

				Vec3d v = this.getVelocity();
				this.applySlowdown();
				prevVelocity *= this.getVelocity().length() / v.length();
				this.setVelocity(v);


				if (prevVelocity > this.getMaxSpeed()) {
					prevVelocity = this.getMaxSpeed();
				}
				distanceMoved += distance;
				if (newSpeed > maxSpeed) {
					currentVelocity = this.getVelocity().normalize().multiply(Math.min(maxSpeed, Math.abs(prevVelocity - distanceMoved)));
				} else {
					currentVelocity = this.getVelocity().normalize().multiply(newSpeed);
				}

				if (railState.isOf(Blocks.ACTIVATOR_RAIL)) {
					this.onActivatorRail(railPos.getX(), railPos.getY(), railPos.getZ(), railState.get(PoweredRailBlock.POWERED));
				}
			}

			this.setVelocity(this.getVelocity().normalize().multiply(prevVelocity));

		} else {
			moveOnRail.call(instance, pos, state);
		}
	}

	@ModifyExpressionValue(
		method = "moveOnRail",
		at = {
				@At(
						value = "CONSTANT",
						args = "doubleValue=0.75",
						ordinal = 0
				),
				@At(
						value = "CONSTANT",
						args = "doubleValue=1.0",
						ordinal = 0
				)
		}
	)
	public double increaseTopSpeedFactor(double value) {
		if (value == 0.75) {
			return getValue(value, FasterMinecartsConfig.getTopSpeedFactorNoPassenger());
		} else if (value == 1.0) {
			return getValue(value, FasterMinecartsConfig.getTopSpeedFactorWithPassenger());
		}
		return value;
	}

	@Unique
	private double getValue(double defaultValue, EntityFactorConfig config) {
		if (!hasSuperSpeed()) {
			return defaultValue;
		}
		double configValue = config.getValue(this.getType());
		if (Double.isNaN(configValue)) {
			return defaultValue;
		} else {
			return configValue;
		}
	}

	@Unique
	private double applyMaxSpeedFromBlockBellow(double prevMaxSpeed) {
		Block block = this.getWorld().getBlockState(getBlockPos().down()).getBlock();
		var boost = FasterMinecartsConfig.getBlockBoosters().getValue(block);
		if (!Double.isNaN(boost)) {
			return prevMaxSpeed + boost;
		}
		return prevMaxSpeed;
	}

	@ModifyExpressionValue(method = "getMaxSpeed", at = @At(value = "CONSTANT", args = "doubleValue=8.0"))
	protected double modifyMaxSpeed(double speed) {
		if (hasSuperSpeed()) {
			return applyMaxSpeedFromBlockBellow(maxSpeed.orElse(FasterMinecartsConfig.getConfig().maxMinecartSpeed));
		}
		return speed;
	}

	@ModifyExpressionValue(method = "getMaxSpeed", at = @At(value = "CONSTANT", args = "doubleValue=4.0"))
	protected double modifyMaxSpeedUnderwater(double speed) {
		if (hasSuperSpeed()) {
			return applyMaxSpeedFromBlockBellow(maxSpeedUnderwater.orElse(FasterMinecartsConfig.getConfig().maxMinecartSpeedUnderwater));
		}
		return speed;
	}

	@Unique
	private static final String ITEM_NAME = "FastItemName";

	@Inject(method = "writeCustomDataToNbt", at = @At("RETURN"))
	public void toNBT(NbtCompound nbt, CallbackInfo ci) {
		nbt.putBoolean(FasterMinecarts.SPEED_UPGRADE_KEY, hasSpeedUpgrade);
		acceleration.ifPresent(a -> nbt.putDouble(FasterMinecarts.ACCELERATION, a));
		maxSpeed.ifPresent(m -> nbt.putDouble(FasterMinecarts.MAX_SPEED, m));
		maxSpeedUnderwater.ifPresent(m -> nbt.putDouble(FasterMinecarts.MAX_SPEED_UNDERWATER, m));
		craftingTag.ifPresent(t -> nbt.putString(FasterMinecarts.CRAFTING_TAG, t));
		itemName.flatMap(t -> TextCodecs.CODEC.encodeStart(NbtOps.INSTANCE, t).resultOrPartial(
				FasterMinecarts.logger::error
		)).ifPresent(data -> nbt.put(ITEM_NAME, data));
	}

	@Inject(method = "readCustomDataFromNbt", at = @At("RETURN"))
	public void fromNBT(NbtCompound nbt, CallbackInfo ci) {
		hasSpeedUpgrade = nbt.getBoolean(FasterMinecarts.SPEED_UPGRADE_KEY);
		if (nbt.contains(FasterMinecarts.ACCELERATION)) {
			acceleration = OptionalDouble.of(nbt.getDouble(FasterMinecarts.ACCELERATION));
		} else {
			acceleration = OptionalDouble.empty();
		}
		if (nbt.contains(FasterMinecarts.MAX_SPEED)) {
			maxSpeed = OptionalDouble.of(nbt.getDouble(FasterMinecarts.MAX_SPEED));
		} else {
			maxSpeed = OptionalDouble.empty();
		}
		if (nbt.contains(FasterMinecarts.MAX_SPEED_UNDERWATER)) {
			maxSpeedUnderwater = OptionalDouble.of(nbt.getDouble(FasterMinecarts.MAX_SPEED_UNDERWATER));
		} else {
			maxSpeedUnderwater = OptionalDouble.empty();
		}
		if (nbt.contains(FasterMinecarts.CRAFTING_TAG)) {
			craftingTag = Optional.of(nbt.getString(FasterMinecarts.CRAFTING_TAG));
		} else {
			craftingTag = Optional.empty();
		}
		if (nbt.contains(ITEM_NAME)) {
			itemName = TextCodecs.CODEC.parse(NbtOps.INSTANCE, nbt.get(ITEM_NAME)).resultOrPartial(
					FasterMinecarts.logger::error
			);
		} else {
			itemName = Optional.empty();
		}
	}

	@Override
	public void fasterMinecarts$setSuperFast(boolean superFast) {
		this.hasSpeedUpgrade = superFast;
	}
	@Override
	public void fasterMinecarts$setAcceleration(OptionalDouble acceleration) {
		this.acceleration = acceleration;
	}
	@Override
	public void fasterMinecarts$setMaxSpeed(OptionalDouble maxSpeed) {
		this.maxSpeed = maxSpeed;
	}
	@Override
	public void fasterMinecarts$setMaxSpeedUnderwater(OptionalDouble maxSpeedUnderwater) {
		this.maxSpeedUnderwater = maxSpeedUnderwater;
	}
	@Override
	public void fasterMinecarts$setCraftingTag(Optional<String> craftingTag) {
		this.craftingTag = craftingTag;
	}
	@Override
	public void fasterMinecarts$setItemName(Optional<Text> itemName) {
		this.itemName = itemName;
	}


	@Override
	public boolean fasterMinecarts$isSuperFast() {
		return hasSpeedUpgrade;
	}

	@Override
	public OptionalDouble fasterMinecarts$getAcceleration() {
		return acceleration;
	}

	@Override
	public OptionalDouble fasterMinecarts$getMaxSpeed() {
		return maxSpeed;
	}

	@Override
	public OptionalDouble fasterMinecarts$getMaxSpeedUnderwater() {
		return maxSpeedUnderwater;
	}

	@Override
	public Optional<String> fasterMinecarts$getCraftingTag() {
		return craftingTag;
	}

	@Override
	public Optional<Text> fasterMinecarts$getItemName() {
		return itemName;
	}
}
