package nu.metacraft.cutscenes;

import net.minecraft.FileUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import nu.metacraft.cutscenes.cutscene.Cutscene;
import nu.metacraft.cutscenes.extension.MinecraftServerExtension;
import nu.metacraft.cutscenes.util.DefaultCutscenes;
import nu.metacraft.lib.config.JsonHelper;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class CutscenesConfig {

	private final Map<String, Cutscene> cutscenes;

	public static CutscenesConfig getOrCreateConfig(
			MinecraftServer server
	) {
		var s = (MinecraftServerExtension) server;
		var config = s.metacraft_cutscenes$getConfig();
		if (config != null) return config;
		var path = server.getWorldPath(LevelResource.ROOT).resolve("metacraft-cutscenes");
		var dir = path.toFile();
		var codec = Cutscene.CODEC.codec();
		if (!dir.exists()) {
			dir.mkdirs();
			config = new CutscenesConfig();
			for (var name : config.getCutsceneNames()) {
				String actualName = FileUtil.sanitizeName(name);
				config.getCutscene(actualName).ifPresent(scene -> {
					JsonHelper.save(
							path.resolve(actualName + ".json"), codec,
							scene, server.registryAccess()
					);
				});
			}
			s.metacraft_cutscenes$setConfig(config);
			return config;
		}
		Map<String, Cutscene> cutscenes = new HashMap<>();
		var files = dir.listFiles((file, name) -> name.endsWith(".json"));
		if (files != null) {
			for (var f : files) {
				JsonHelper.load(f.toPath(), codec, server.registryAccess()).ifPresent(
						cutscene -> cutscenes.put(f.getName().substring(0, f.getName().length()-5), cutscene)
				);
			}
		}
		config = new CutscenesConfig(cutscenes);
		s.metacraft_cutscenes$setConfig(config);
		return config;
	}

	public CutscenesConfig() {
		this(new HashMap<>(Map.of(
				"creeper_kill_piglin", DefaultCutscenes.CREEPER_KILL_PIGLIN
		)));
	}

	public CutscenesConfig(Map<String, Cutscene> map) {
		this.cutscenes = map;
	}

	public static void reload(MinecraftServer server) {
		((MinecraftServerExtension) server).metacraft_cutscenes$setConfig(null);
	}

	public Optional<Cutscene> getCutscene(String name) {
		return cutscenes.containsKey(name) ? Optional.ofNullable(cutscenes.get(name)) : Optional.empty();
	}

	public Collection<String> getCutsceneNames() {
		return cutscenes.keySet();
	}

}
