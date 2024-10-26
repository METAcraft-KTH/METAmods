package se.datasektionen.mc.faster_minecarts.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import se.datasektionen.mc.faster_minecarts.FasterMinecarts;
import se.datasektionen.mc.faster_minecarts.MinecartData;

import java.util.Optional;
import java.util.OptionalDouble;

@Mixin(AbstractMinecartEntity.class)
public abstract class MixinAbstractMinecartEntity extends Entity implements MinecartData {

	@Shadow protected abstract Vec3d applySlowdown(Vec3d velocity);

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

	@Unique
	private BlockPos currentRailPosOverride;

	public MixinAbstractMinecartEntity(EntityType<?> entityType, World world) {
		super(entityType, world);
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

	@Inject(
		method = "getRailOrMinecartPos",
		at = @At("HEAD"),
		cancellable = true
	)
	public void getRailOrMinecartPos(CallbackInfoReturnable<BlockPos> cir) {
		if (currentRailPosOverride != null) {
			cir.setReturnValue(currentRailPosOverride);
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

	@Override
	public void fasterMinecarts$setCurrentRailPosOverride(BlockPos pos) {
		this.currentRailPosOverride = pos;
	}

	@Override
	public void fasterMinecarts$applySlowdown(Vec3d velocity) {
		this.applySlowdown(velocity);
	}
}
