package nu.metacraft.faster_minecarts;

import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.Optional;
import java.util.OptionalDouble;

public interface MinecartExtensions {

	enum SuperSpeedState {
		TRUE,
		FALSE,
		DEFAULT;

		boolean getValue(boolean fallback) {
			return switch (this) {
				case TRUE -> true;
				case FALSE -> false;
				case DEFAULT -> fallback;
			};
		}
	}

	SuperSpeedState fasterMinecarts$speedUpgrade();

	OptionalDouble fasterMinecarts$getAcceleration();
	OptionalDouble fasterMinecarts$getMaxSpeed();
	OptionalDouble fasterMinecarts$getMaxSpeedUnderwater();

	Optional<ItemStack> fasterMinecarts$getMinecartItem();
	void fasterMinecarts$setMinecartItem(Optional<ItemStack> stack);

	void fasterMinecarts$setCurrentRailPosOverride(BlockPos pos);
	void fasterMinecarts$applySlowdown(Vec3d velocity);

	void fasterMinecarts$setInitialZ(Direction.AxisDirection direction);
	Direction.AxisDirection fasterMinecarts$getInitialZ();

	boolean fasterMinecarts$yawFixed();

	void fasterMinecarts$setYawFixed();

}
