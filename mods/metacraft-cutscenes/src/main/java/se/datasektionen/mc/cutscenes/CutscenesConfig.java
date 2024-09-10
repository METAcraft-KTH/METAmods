package se.datasektionen.mc.cutscenes;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.world.ServerWorld;
import se.datasektionen.mc.cutscenes.cutscene.Cutscene;
import se.datasektionen.mc.cutscenes.util.DefaultCutscenes;
import se.datasektionen.mc.metacraft_lib.config.JsonPersistentState;

import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class CutscenesConfig extends JsonPersistentState {

	private static final String KEY = "metacraft-cutscenes";

	public static final Codec<CutscenesConfig> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
					Codec.unboundedMap(Codec.STRING, Cutscene.CODEC.codec()).fieldOf("cutscenes").forGetter(c -> c.cutscenes)
			).apply(instance, CutscenesConfig::new)
	);

	private static final JsonType<CutscenesConfig> TYPE = new JsonType<>(
			CutscenesConfig::new, CODEC
	);

	public static Optional<CutscenesConfig> getLoadedConfig(ServerWorld world) {
		return getLoadedConfig(world, TYPE, KEY);
	}

	public static Optional<CutscenesConfig> getConfig(ServerWorld world) {
		return getConfig(world, TYPE, KEY);
	}

	public static CutscenesConfig getOrCreateConfig(ServerWorld world) {
		return getOrCreateConfig(world, TYPE, KEY);
	}

	private final Map<String, Cutscene> cutscenes;

	private ServerWorld world;

	public CutscenesConfig() {
		this(new HashMap<>(Map.of(
				"creeper_kill_piglin", DefaultCutscenes.CREEPER_KILL_PIGLIN
		)));
	}

	public CutscenesConfig(Map<String, Cutscene> cutscenes) {
		this.cutscenes = cutscenes;
	}

	@Override
	public void prepare(ServerWorld world, Path path, String key, Codec<JsonPersistentState> codec) {
		super.prepare(world, path, key, codec);
		this.world = world;
	}

	public ServerWorld getWorld() {
		return world;
	}

	public void reload() {
		reload(world);
	}

	public Optional<Cutscene> getCutscene(String name) {
		return cutscenes.containsKey(name) ? Optional.ofNullable(cutscenes.get(name)) : Optional.empty();
	}

	public Collection<String> getCutsceneNames() {
		return cutscenes.keySet();
	}

}
