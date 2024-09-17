package se.datasektionen.mc.cutscenes;

import com.mojang.serialization.Codec;
import net.minecraft.server.world.ServerWorld;
import se.datasektionen.mc.cutscenes.cutscene.Cutscene;
import se.datasektionen.mc.cutscenes.util.DefaultCutscenes;
import se.datasektionen.mc.metacraft_lib.config.JsonPersistentState;
import se.datasektionen.mc.metacraft_lib.config.MultiPersistentState;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class CutscenesConfig extends MultiPersistentState<CutscenesConfig.CutsceneEntry> {

	private static final CustomType<CutscenesConfig> TYPE = new CustomType<>(
			CutscenesConfig::new, new MultiParser<>(
					CutsceneEntry.ENTRY_TYPE, CutscenesConfig::new
			)
	);

	private static final String KEY = "metacraft-cutscenes";

	public static Optional<CutscenesConfig> getLoadedConfig(ServerWorld world) {
		return getLoadedConfig(world, TYPE, KEY);
	}

	public static Optional<CutscenesConfig> getConfig(ServerWorld world) {
		return getConfig(world, TYPE, KEY);
	}

	public static CutscenesConfig getOrCreateConfig(ServerWorld world) {
		return getOrCreateConfig(world, TYPE, KEY);
	}

	public CutscenesConfig() {
		this(new HashMap<>(Map.of(
				"creeper_kill_piglin", new CutsceneEntry(DefaultCutscenes.CREEPER_KILL_PIGLIN)
		)));
	}

	public CutscenesConfig(Map<String, CutscenesConfig.CutsceneEntry> map) {
		super(map);
	}

	public ServerWorld getWorld() {
		return world;
	}

	public Optional<Cutscene> getCutscene(String name) {
		return states.containsKey(name) ? Optional.ofNullable(states.get(name).cutscene) : Optional.empty();
	}

	public Collection<String> getCutsceneNames() {
		return states.keySet();
	}

	public static class CutsceneEntry extends JsonPersistentState {

		public static final Codec<CutsceneEntry> CODEC = Cutscene.CODEC.codec().xmap(CutsceneEntry::new, CutsceneEntry::getCutscene);
		public static final CustomType<CutsceneEntry> ENTRY_TYPE = new CustomType<>(
				CutsceneEntry::new, new JsonParse<>(CODEC)
		);

		private final Cutscene cutscene;

		public CutsceneEntry() {
			this(new Cutscene());
		}

		public CutsceneEntry(Cutscene cutscene) {
			this.cutscene = cutscene;
		}

		public Cutscene getCutscene() {
			return cutscene;
		}

	}

}
