package se.datasektionen.mc.metacraft_lib.config.container;

public interface ConfigContainerWithSingleton<T> {

	/**
	 * Returns the config.
	 * If the config is not loaded, it will be loaded.
	 * If the config could not be loaded (for example, because it had not been generated yet),
	 * a config will be generated with default parameters.
	 * Users are advice to NOT store this in variables for longer periods of time as doing so will break the reload functionality.
	 * @return The config instance.
	 */
	T get();

}
