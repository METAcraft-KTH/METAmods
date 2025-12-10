package nu.metacraft.faster_minecarts;

import java.util.Optional;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class FasterMinecartsHelper {

	public static boolean hasSuperSpeed(AbstractMinecart minecart) {
		return ((MinecartExtensions) minecart).fasterMinecarts$speedUpgrade().getValue(FasterMinecartsConfig.getConfig().globalFasterMinecarts());
	}

	public static double getValue(AbstractMinecart minecart, double defaultValue, Function<FasterMinecartsConfig.MinecartModifier, Optional<Double>> valueGetter) {
		if (!hasSuperSpeed(minecart)) {
			return defaultValue;
		}

		double configValue = FasterMinecartsConfig.getConfig(minecart.level().getServer()).getRelevantModifiers(minecart).map(valueGetter).filter(
				Optional::isPresent
		).mapToDouble(Optional::get).max().orElse(defaultValue);
		if (Double.isNaN(configValue)) {
			return defaultValue;
		} else {
			return configValue;
		}
	}

	public static double getActualMaxSpeed(
			AbstractMinecart minecart, double defaultMaxSpeed
	) {
		if (hasSuperSpeed(minecart)) {
			return applyMaxSpeedFromBlockBellow(
					minecart.level(), minecart.blockPosition(),
					minecart.isInWater() ? ((MinecartExtensions) minecart).fasterMinecarts$getMaxSpeedUnderwater().orElse(
							FasterMinecartsConfig.getConfig().maxMinecartSpeedUnderwater()
					) : ((MinecartExtensions) minecart).fasterMinecarts$getMaxSpeed().orElse(
							FasterMinecartsConfig.getConfig().maxMinecartSpeed()
					)
			)/20;
		}
		return defaultMaxSpeed;
	}

	public static double applyMaxSpeedFromBlockBellow(Level world, BlockPos pos, double maxSpeed) {
		if (!(world instanceof ServerLevel)) return maxSpeed;
		var boost = FasterMinecartsConfig.getConfig(world.getServer()).getBlockBoost((ServerLevel) world, pos);
		if (!Double.isNaN(boost)) {
			return maxSpeed + boost;
		}
		return maxSpeed;
	}

	public static boolean areMinecartExperimentsEnabledForCart(boolean worldSetting, AbstractMinecart minecart) {
		return areMinecartExperimentsEnabledForCart(worldSetting, hasSuperSpeed(minecart));
	}

	public static boolean areMinecartExperimentsEnabledForCart(boolean worldSetting, boolean superSpeed) {
		if (FasterMinecartsConfig.getConfig().experimentalMinecartMode().isEnabled() && superSpeed) {
			return true;
		}
		return worldSetting;
	}

	public static Optional<ItemStack> getMinecartItem(AbstractMinecart minecart) {
		return ((MinecartExtensions) minecart).fasterMinecarts$getMinecartItem();
	}
}
