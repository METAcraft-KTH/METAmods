package se.datasektionen.mc.faster_minecarts;

import net.minecraft.block.Block;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import se.datasektionen.mc.faster_minecarts.configs.EntityFactorConfig;

public class FasterMinecartsHelper {

	public static boolean hasSuperSpeed(AbstractMinecartEntity minecart) {
		return ((MinecartData) minecart).fasterMinecarts$isSuperFast() || FasterMinecartsConfig.getConfig().globalFasterMinecarts;
	}

	public static double getValue(AbstractMinecartEntity minecart, double defaultValue, EntityFactorConfig config) {
		if (!hasSuperSpeed(minecart)) {
			return defaultValue;
		}
		double configValue = config.getValue(minecart.getType());
		if (Double.isNaN(configValue)) {
			return defaultValue;
		} else {
			return configValue;
		}
	}

	public static double getActualMaxSpeed(
			AbstractMinecartEntity minecart, double defaultMaxSpeed
	) {
		if (hasSuperSpeed(minecart)) {
			return applyMaxSpeedFromBlockBellow(
					minecart.getWorld(), minecart.getBlockPos(),
					((MinecartData) minecart).fasterMinecarts$getMaxSpeed().orElse(
							minecart.isTouchingWater() ? FasterMinecartsConfig.getConfig().maxMinecartSpeedUnderwater : FasterMinecartsConfig.getConfig().maxMinecartSpeed
					)
			)/20;
		}
		return defaultMaxSpeed;
	}

	public static double applyMaxSpeedFromBlockBellow(World world, BlockPos pos, double maxSpeed) {
		Block block = world.getBlockState(pos.down()).getBlock();
		var boost = FasterMinecartsConfig.getBlockBoosters().getValue(block);
		if (!Double.isNaN(boost)) {
			return maxSpeed + boost;
		}
		return maxSpeed;
	}

}
