package se.datasektionen.mc.metacraft_lib.config.container;

import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Supplier;

@FunctionalInterface
public interface ReloadFunction<T> {
	T reload(@Nullable T oldConfig, Supplier<Optional<T>> newConfig, ReloadCause cause);

	static <T> ReloadFunction<T> getDefault() {
		return (oldConfig, newConfig, cause) -> newConfig.get().orElse(oldConfig);
	}

}
