package nu.metacraft.faster_minecarts.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.MinecartBehavior;
import net.minecraft.world.entity.vehicle.NewMinecartBehavior;
import net.minecraft.world.entity.vehicle.OldMinecartBehavior;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import nu.metacraft.faster_minecarts.FasterMinecarts;
import nu.metacraft.faster_minecarts.FasterMinecartsHelper;
import nu.metacraft.faster_minecarts.MinecartComponents;
import nu.metacraft.faster_minecarts.MinecartExtensions;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;

@Mixin(AbstractMinecart.class)
public abstract class AbstractMinecartMixin extends VehicleEntity implements MinecartExtensions {

	@Shadow protected abstract Vec3 applyNaturalSlowdown(Vec3 velocity);

	@Shadow
	public static boolean useExperimentalMovement(Level world) {
		throw new IllegalStateException("Mixin Error");
	}

	@Unique
	private static final String MINECART_TAG = "faster_minecarts.has_super_speed";

	@Unique
	private Direction.AxisDirection initialZ = Direction.AxisDirection.POSITIVE;
	@Unique
	private boolean yawFixed = false;

	@Shadow @Final @Mutable
	private MinecartBehavior behavior;

	@Unique
	private Optional<ItemStack> minecartItem = Optional.empty();

	@Unique
	private BlockPos currentRailPosOverride;

	public AbstractMinecartMixin(EntityType<?> entityType, Level world) {
		super(entityType, world);
	}

	@Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
	public void toNBT(ValueOutput nbt, CallbackInfo ci) {
		minecartItem.ifPresent(
				item -> {
					if (!item.isEmpty()) {
						nbt.store(FasterMinecarts.MINECART_ITEM, ItemStack.CODEC, item);
					}
				}
		);

	}

	@Unique
	private void updateTag() {
		if (FasterMinecartsHelper.hasSuperSpeed((AbstractMinecart) (Object) this)) {
			getTags().add(MINECART_TAG);
		} else {
			getTags().remove(MINECART_TAG);
		}
	}

	@Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
	public void fromNBT(ValueInput nbt, CallbackInfo ci) {
		minecartItem = nbt.read(FasterMinecarts.MINECART_ITEM, ItemStack.CODEC);
		updateTag();
		updateController();
	}

	@Unique
	private void trySetController(Class<? extends MinecartBehavior> clazz, Supplier<MinecartBehavior> controllerCreator) {
		if (!clazz.isInstance(behavior)) {
			this.behavior = controllerCreator.get();
		}
	}

	@Unique
	private void updateController() {
		if (FasterMinecartsHelper.areMinecartExperimentsEnabledForCart(
				useExperimentalMovement(level()),(AbstractMinecart) (Object) this
		)) {
			trySetController(
					NewMinecartBehavior.class,
					() -> new NewMinecartBehavior((AbstractMinecart) (Object) this)
			);
		} else {
			trySetController(
					OldMinecartBehavior.class,
					() -> new OldMinecartBehavior((AbstractMinecart) (Object) this)
			);
		}
	}

	@Inject(
		method = "getCurrentBlockPosOrRailBelow",
		at = @At("HEAD"),
		cancellable = true
	)
	public void getRailOrMinecartPos(CallbackInfoReturnable<BlockPos> cir) {
		if (currentRailPosOverride != null) {
			cir.setReturnValue(currentRailPosOverride);
		}
	}


	@Override
	public SuperSpeedState fasterMinecarts$speedUpgrade() {
		return minecartItem.map(item -> item.get(MinecartComponents.SPEED_UPGRADE)).map(
				val -> val ? SuperSpeedState.TRUE : SuperSpeedState.FALSE
		).orElse(SuperSpeedState.DEFAULT);
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
	public void fasterMinecarts$applySlowdown(Vec3 velocity) {
		this.applyNaturalSlowdown(velocity);
	}

	@ModifyExpressionValue(
		method = "applyNaturalSlowdown",
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
			"getCurrentBlockPosOrRailBelow", "move", "applyEffectsFromBlocks", "pushOtherMinecart"
		},
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/vehicle/AbstractMinecart;useExperimentalMovement(Lnet/minecraft/world/level/Level;)Z"
		)
	)
	public boolean checkIfCart(boolean original) {
		return FasterMinecartsHelper.areMinecartExperimentsEnabledForCart(original, (AbstractMinecart) (Object) this);
	}

	@Inject(
		method = "createMinecart",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/vehicle/AbstractMinecart;getBehavior()Lnet/minecraft/world/entity/vehicle/MinecartBehavior;"
		)
	)
	private static <T extends AbstractMinecart> void create(
			Level world, double x, double y, double z, EntityType<T> type,
			EntitySpawnReason reason, ItemStack stack, Player player, CallbackInfoReturnable<T> cir,
			@Local AbstractMinecart minecart
	) {
		if (!stack.isEmpty()) {
			((MinecartExtensions) minecart).fasterMinecarts$setMinecartItem(Optional.of(stack.copyWithCount(1)));
		}
		if (player != null && Math.abs(player.getYRot()) > 90) {
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
