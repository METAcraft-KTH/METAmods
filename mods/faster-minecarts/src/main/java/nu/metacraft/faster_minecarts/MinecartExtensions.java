package nu.metacraft.faster_minecarts;

import java.util.Optional;
import java.util.OptionalDouble;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

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
	void fasterMinecarts$applySlowdown(Vec3 velocity);

	void fasterMinecarts$setInitialZ(Direction.AxisDirection direction);
	Direction.AxisDirection fasterMinecarts$getInitialZ();

	boolean fasterMinecarts$yawFixed();

	void fasterMinecarts$setYawFixed();

}
