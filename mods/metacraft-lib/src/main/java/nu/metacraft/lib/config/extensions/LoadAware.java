package nu.metacraft.lib.config.extensions;

import nu.metacraft.lib.config.container.ReloadCause;

import java.util.Optional;

public interface LoadAware {

	void afterLoad(Optional<ReloadCause> cause);

}
