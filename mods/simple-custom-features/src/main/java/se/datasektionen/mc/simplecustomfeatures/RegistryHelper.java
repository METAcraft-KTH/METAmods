package se.datasektionen.mc.simplecustomfeatures;

import net.minecraft.registry.Registry;
import net.minecraft.registry.SimpleRegistry;

public class RegistryHelper {

	public static boolean unlockRegistry(Registry<?> registry) {
		if (registry instanceof SimpleRegistry<?>) {
			return ((RegistryExtensions) registry).simpleCustomFeatures$unfreezeRegistry();
		}
		return false;
	}

	public static void lockRegistry(Registry<?> registry) {
		if (registry instanceof SimpleRegistry<?>) {
			registry.freeze();
		}
	}

}
