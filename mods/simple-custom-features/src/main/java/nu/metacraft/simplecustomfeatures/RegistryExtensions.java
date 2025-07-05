package nu.metacraft.simplecustomfeatures;

public interface RegistryExtensions<T> {

	boolean simpleCustomFeatures$unfreezeRegistry();
	void simpleCustomFeatures$remove(T value);
	void simpleCustomFeatures$removeIntrusiveEntry(T value);

	boolean simpleCustomFeatures$isFrozen();

}
