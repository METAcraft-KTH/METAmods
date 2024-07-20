package se.datasektionen.mc.metacraft_lib.config.container;

import java.util.Optional;
import java.util.function.Supplier;

@FunctionalInterface
public interface ReloadFunction<T> {
	T reload(T oldConfig, Supplier<Optional<T>> newConfig, ReloadCause cause);

	static <T> ReloadFunction<T> getDefault() {
		return (oldConfig, newConfig, cause) -> newConfig.get().orElse(oldConfig);
	}

}
