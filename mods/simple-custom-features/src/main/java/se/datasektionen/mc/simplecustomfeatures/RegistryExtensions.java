package se.datasektionen.mc.simplecustomfeatures;

public interface RegistryExtensions<T> {

	boolean simpleCustomFeatures$unfreezeRegistry();
	void simpleCustomFeatures$remove(T value);
	void simpleCustomFeatures$removeIntrusiveEntry(T value);

}
