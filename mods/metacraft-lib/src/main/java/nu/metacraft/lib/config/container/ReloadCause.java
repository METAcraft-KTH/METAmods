package nu.metacraft.lib.config.container;

import net.minecraft.util.Identifier;
import nu.metacraft.lib.METAcraftLib;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ReloadCause {

	private static final Map<Identifier, ReloadCause> INSTANCES = new ConcurrentHashMap<>();

	private final Identifier id;

	private ReloadCause(Identifier id) {
		this.id = id;
	}

	public static final ReloadCause DEFAULT = ReloadCause.of(METAcraftLib.getID("default"));
	public static final ReloadCause BEFORE_SERVER_RELOAD = ReloadCause.of(METAcraftLib.getID("before_server_reload"));
	public static final ReloadCause AFTER_SERVER_RELOAD = ReloadCause.of(METAcraftLib.getID("after_server_reload"));
	public static final ReloadCause REFRESH_CACHE_AFTER_MODIFY = ReloadCause.of(METAcraftLib.getID("refresh_cache_after_modify"));

	public static ReloadCause of(Identifier id) {
		return INSTANCES.computeIfAbsent(id, ReloadCause::new);
	}

	public Identifier getID() {
		return id;
	}
}
