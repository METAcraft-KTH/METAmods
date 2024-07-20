package se.datasektionen.mc.metacraft_lib.config.extensions;

import se.datasektionen.mc.metacraft_lib.config.container.ReloadCause;

import java.util.Optional;

public interface LoadAware {

	void afterLoad(Optional<ReloadCause> cause);

}
