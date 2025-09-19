package nu.metacraft.faster_minecarts;

import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Optional;
import java.util.function.Function;

public class FasterMinecartsHelper {

	public static boolean hasSuperSpeed(AbstractMinecartEntity minecart) {
		return ((MinecartExtensions) minecart).fasterMinecarts$speedUpgrade().getValue(FasterMinecartsConfig.getConfig().globalFasterMinecarts());
	}

	public static double getValue(AbstractMinecartEntity minecart, double defaultValue, Function<FasterMinecartsConfig.MinecartModifier, Optional<Double>> valueGetter) {
		if (!hasSuperSpeed(minecart)) {
			return defaultValue;
		}

		double configValue = FasterMinecartsConfig.getConfig(minecart.getEntityWorld().getServer()).getRelevantModifiers(minecart).map(valueGetter).filter(
				Optional::isPresent
		).mapToDouble(Optional::get).max().orElse(defaultValue);
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
					minecart.getEntityWorld(), minecart.getBlockPos(),
					minecart.isTouchingWater() ? ((MinecartExtensions) minecart).fasterMinecarts$getMaxSpeedUnderwater().orElse(
							FasterMinecartsConfig.getConfig().maxMinecartSpeedUnderwater()
					) : ((MinecartExtensions) minecart).fasterMinecarts$getMaxSpeed().orElse(
							FasterMinecartsConfig.getConfig().maxMinecartSpeed()
					)
			)/20;
		}
		return defaultMaxSpeed;
	}

	public static double applyMaxSpeedFromBlockBellow(World world, BlockPos pos, double maxSpeed) {
		if (!(world instanceof ServerWorld)) return maxSpeed;
		var boost = FasterMinecartsConfig.getConfig(world.getServer()).getBlockBoost((ServerWorld) world, pos);
		if (!Double.isNaN(boost)) {
			return maxSpeed + boost;
		}
		return maxSpeed;
	}

	public static boolean areMinecartExperimentsEnabledForCart(boolean worldSetting, AbstractMinecartEntity minecart) {
		return areMinecartExperimentsEnabledForCart(worldSetting, hasSuperSpeed(minecart));
	}

	public static boolean areMinecartExperimentsEnabledForCart(boolean worldSetting, boolean superSpeed) {
		if (FasterMinecartsConfig.getConfig().experimentalMinecartMode().isEnabled() && superSpeed) {
			return true;
		}
		return worldSetting;
	}

	public static Optional<ItemStack> getMinecartItem(AbstractMinecartEntity minecart) {
		return ((MinecartExtensions) minecart).fasterMinecarts$getMinecartItem();
	}
}
