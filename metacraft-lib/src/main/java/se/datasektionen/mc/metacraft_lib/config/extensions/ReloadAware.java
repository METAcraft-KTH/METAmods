package se.datasektionen.mc.metacraft_lib.config.extensions;

import se.datasektionen.mc.metacraft_lib.config.container.ReloadCause;

public interface ReloadAware {

	void beforeReload(ReloadCause cause);

}
