package se.datasektionen.mc.faster_minecarts;

import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Optional;
import java.util.OptionalDouble;

public interface MinecartData {

	void fasterMinecarts$setSuperFast(boolean superFast);

	boolean fasterMinecarts$isSuperFast();

	void fasterMinecarts$setAcceleration(OptionalDouble acceleration);

	OptionalDouble fasterMinecarts$getAcceleration();
	void fasterMinecarts$setMaxSpeed(OptionalDouble maxSpeed);
	OptionalDouble fasterMinecarts$getMaxSpeed();
	void fasterMinecarts$setMaxSpeedUnderwater(OptionalDouble maxSpeedUnderwater);
	OptionalDouble fasterMinecarts$getMaxSpeedUnderwater();
	void fasterMinecarts$setCraftingTag(Optional<String> craftingTag);
	Optional<String> fasterMinecarts$getCraftingTag();

	void fasterMinecarts$setItemName(Optional<Text> itemName);
	Optional<Text> fasterMinecarts$getItemName();

	void fasterMinecarts$setCurrentRailPosOverride(BlockPos pos);
	void fasterMinecarts$applySlowdown(Vec3d velocity);

}
