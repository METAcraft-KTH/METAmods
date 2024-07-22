package se.datasektionen.mc.simplecustomfeatures;

import net.minecraft.registry.RegistryKey;

public interface RegistryExtensions<T> {

	boolean simpleCustomFeatures$unfreezeRegistry();
	void simpleCustomFeatures$remove(T value);
	void simpleCustomFeatures$addLegacyRef(RegistryKey<T> key, T value);
	void simpleCustomFeatures$removeIntrusiveEntry(T value);

}
