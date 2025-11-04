package nu.metacraft.lib.config.container;

import nu.metacraft.lib.METAcraftLib;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;

public class ReloadCause {

	private static final Map<ResourceLocation, ReloadCause> INSTANCES = new ConcurrentHashMap<>();

	private final ResourceLocation id;

	private ReloadCause(ResourceLocation id) {
		this.id = id;
	}

	public static final ReloadCause DEFAULT = ReloadCause.of(METAcraftLib.getID("default"));
	public static final ReloadCause BEFORE_SERVER_RELOAD = ReloadCause.of(METAcraftLib.getID("before_server_reload"));
	public static final ReloadCause AFTER_SERVER_RELOAD = ReloadCause.of(METAcraftLib.getID("after_server_reload"));
	public static final ReloadCause REFRESH_CACHE_AFTER_MODIFY = ReloadCause.of(METAcraftLib.getID("refresh_cache_after_modify"));

	public static ReloadCause of(ResourceLocation id) {
		return INSTANCES.computeIfAbsent(id, ReloadCause::new);
	}

	public ResourceLocation getID() {
		return id;
	}
}
