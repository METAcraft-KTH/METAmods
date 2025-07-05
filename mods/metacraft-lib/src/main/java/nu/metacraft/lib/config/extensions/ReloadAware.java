package nu.metacraft.lib.config.extensions;

import nu.metacraft.lib.config.container.ReloadCause;

public interface ReloadAware {

	void beforeReload(ReloadCause cause);

}
