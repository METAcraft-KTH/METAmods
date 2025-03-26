package se.datasektionen.mc.faster_minecarts.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import se.datasektionen.mc.faster_minecarts.FasterMinecarts;
import se.datasektionen.mc.faster_minecarts.FasterMinecartsHelper;
import se.datasektionen.mc.faster_minecarts.MinecartComponents;
import se.datasektionen.mc.faster_minecarts.MinecartExtensions;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;

@Mixin(AbstractMinecartEntity.class)
public abstract class MixinAbstractMinecartEntity extends VehicleEntity implements MinecartExtensions {

	@Shadow protected abstract Vec3d applySlowdown(Vec3d velocity);

	@Shadow
	public static boolean areMinecartImprovementsEnabled(World world) {
		throw new IllegalStateException("Mixin Error");
	}

	@Unique
	private static final String MINECART_TAG = "faster_minecarts.has_super_speed";

	@Unique
	private Direction.AxisDirection initialZ = Direction.AxisDirection.POSITIVE;
	@Unique
	private boolean yawFixed = false;

	@Shadow @Final @Mutable
	private MinecartController controller;

	@Unique
	private Optional<ItemStack> minecartItem = Optional.empty();

	@Unique
	private BlockPos currentRailPosOverride;

	public MixinAbstractMinecartEntity(EntityType<?> entityType, World world) {
		super(entityType, world);
	}

	@Inject(method = "writeCustomDataToNbt", at = @At("RETURN"))
	public void toNBT(NbtCompound nbt, CallbackInfo ci) {
		minecartItem.flatMap(
				m -> ItemStack.CODEC.encodeStart(
						getRegistryManager().getOps(NbtOps.INSTANCE),
						m
				).resultOrPartial(FasterMinecarts.LOGGER::error)
		).ifPresent(
				m -> nbt.put(FasterMinecarts.MINECART_ITEM, m)
		);
	}

	@Unique
	private void updateTag() {
		if (FasterMinecartsHelper.hasSuperSpeed((AbstractMinecartEntity) (Object) this)) {
			getCommandTags().add(MINECART_TAG);
		} else {
			getCommandTags().remove(MINECART_TAG);
		}
	}

	@Inject(method = "readCustomDataFromNbt", at = @At("RETURN"))
	public void fromNBT(NbtCompound nbt, CallbackInfo ci) {
		if (nbt.contains(FasterMinecarts.MINECART_ITEM)) {
			minecartItem = ItemStack.CODEC.parse(
					getRegistryManager().getOps(NbtOps.INSTANCE),
					nbt.get(FasterMinecarts.MINECART_ITEM)
			).resultOrPartial(FasterMinecarts.LOGGER::error);
		} else {
			minecartItem = Optional.empty();
		}
		updateTag();
		updateController();
	}

	@Unique
	private void trySetController(Class<? extends MinecartController> clazz, Supplier<MinecartController> controllerCreator) {
		if (!clazz.isInstance(controller)) {
			this.controller = controllerCreator.get();
		}
	}

	@Unique
	private void updateController() {
		if (FasterMinecartsHelper.areMinecartExperimentsEnabledForCart(
				areMinecartImprovementsEnabled(getWorld()),(AbstractMinecartEntity) (Object) this
		)) {
			trySetController(
					ExperimentalMinecartController.class,
					() -> new ExperimentalMinecartController((AbstractMinecartEntity) (Object) this)
			);
		} else {
			trySetController(
					DefaultMinecartController.class,
					() -> new DefaultMinecartController((AbstractMinecartEntity) (Object) this)
			);
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
	public boolean fasterMinecarts$isSuperFast() {
		return minecartItem.map(item -> item.contains(MinecartComponents.SPEED_UPGRADE)).orElse(false);
	}

	@Override
	public OptionalDouble fasterMinecarts$getAcceleration() {
		return minecartItem.stream().map(
				item -> item.get(MinecartComponents.ACCELERATION)
		).filter(Objects::nonNull).mapToDouble(d -> d).findAny();
	}

	@Override
	public OptionalDouble fasterMinecarts$getMaxSpeed() {
		return minecartItem.stream().map(
				item -> item.get(MinecartComponents.MAX_SPEED)
		).filter(Objects::nonNull).mapToDouble(d -> d).findAny();
	}

	@Override
	public OptionalDouble fasterMinecarts$getMaxSpeedUnderwater() {
		return minecartItem.stream().map(
				item -> item.get(MinecartComponents.MAX_SPEED_UNDERWATER)
		).filter(Objects::nonNull).mapToDouble(d -> d).findAny();
	}

	@Override
	public void fasterMinecarts$setCurrentRailPosOverride(BlockPos pos) {
		this.currentRailPosOverride = pos;
	}

	@Override
	public void fasterMinecarts$applySlowdown(Vec3d velocity) {
		this.applySlowdown(velocity);
	}

	@ModifyExpressionValue(
		method = "applySlowdown",
		at = @At(
				value = "CONSTANT",
				args = "doubleValue=0.949999988079071"
		)
	)
	public double changeUnderwaterApplySlowdown(double original) {
		return minecartItem.map(
				item -> item.get(MinecartComponents.UNDERWATER_SLOWDOWN)
		).orElse(original);
	}


	@ModifyExpressionValue(
		method = {
			"getRailOrMinecartPos", "move", "tickBlockCollision", "pushAwayFromMinecart"
		},
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/entity/vehicle/AbstractMinecartEntity;areMinecartImprovementsEnabled(Lnet/minecraft/world/World;)Z"
		)
	)
	public boolean checkIfCart(boolean original) {
		return FasterMinecartsHelper.areMinecartExperimentsEnabledForCart(original, (AbstractMinecartEntity) (Object) this);
	}

	@Inject(
		method = "create",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/entity/vehicle/AbstractMinecartEntity;getController()Lnet/minecraft/entity/vehicle/MinecartController;"
		)
	)
	private static <T extends AbstractMinecartEntity> void create(
			World world, double x, double y, double z, EntityType<T> type,
			SpawnReason reason, ItemStack stack, PlayerEntity player, CallbackInfoReturnable<T> cir,
			@Local AbstractMinecartEntity minecart
	) {
		if (!stack.isEmpty()) {
			((MinecartExtensions) minecart).fasterMinecarts$setMinecartItem(Optional.of(stack.copyWithCount(1)));
		}
		if (player != null && Math.abs(player.getYaw()) > 90) {
			((MinecartExtensions) minecart).fasterMinecarts$setInitialZ(Direction.AxisDirection.NEGATIVE);
		}
	}

	@Override
	public void fasterMinecarts$setInitialZ(Direction.AxisDirection direction) {
		this.initialZ = direction;
	}

	@Override
	public Direction.AxisDirection fasterMinecarts$getInitialZ() {
		return initialZ;
	}

	@Override
	public boolean fasterMinecarts$yawFixed() {
		return yawFixed;
	}

	@Override
	public void fasterMinecarts$setYawFixed() {
		yawFixed = true;
	}

	@Override
	public Optional<ItemStack> fasterMinecarts$getMinecartItem() {
		return minecartItem;
	}

	@Override
	public void fasterMinecarts$setMinecartItem(Optional<ItemStack> stack) {
		this.minecartItem = stack;
		updateController();
		updateTag();
	}
}
