package nu.metacraft.cutscenes.transitions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Uuids;
import net.minecraft.world.GameMode;
import nu.metacraft.cutscenes.util.IntervalMap;
import nu.metacraft.cutscenes.cutscene.CutsceneInstance;
import nu.metacraft.cutscenes.registry.TransitionConfigRegistry;
import nu.metacraft.cutscenes.registry.TransitionRegistry;
import nu.metacraft.cutscenes.transitions.config.TransitionConfig;
import nu.metacraft.cutscenes.transitions.config.TransitionConfigType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

public class SetGameModeTransition implements Transition {

	public static final MapCodec<SetGameModeTransition> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Config.CODEC.forGetter(t -> t.config),
					Codec.unboundedMap(Uuids.STRING_CODEC, GameMode.CODEC).fieldOf("prev_gamemodes").forGetter(t -> t.prevGamemodes)
			).apply(instance, SetGameModeTransition::new)
	);

	private final Config config;
	private final Map<UUID, GameMode> prevGamemodes;

	public SetGameModeTransition(Config config) {
		this.config = config;
		this.prevGamemodes = new HashMap<>();
	}

	public SetGameModeTransition(Config config, Map<UUID, GameMode> prevGamemodes) {
		this.config = config;
		this.prevGamemodes = new HashMap<>(prevGamemodes);
	}

	private void copyPrevGamemodes(Stream<Transition> prevTransitions) {
		prevTransitions.filter(
				t -> t instanceof SetGameModeTransition
		).findAny().ifPresent(
				t -> prevGamemodes.putAll(((SetGameModeTransition) t).prevGamemodes)
		);
	}

	@Override
	public void copyFromPreviousCutscene(CutsceneInstance prev, CutsceneInstance current, IntervalMap.Interval<Transition> interval) {
		if (interval.getStart() == 0) {
			copyPrevGamemodes(prev.getTransitions().getValuesAt(prev.getTransitions().getEnd()));
		}
	}

	@Override
	public void activate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		if (interval.getStart() > 0) {
			copyPrevGamemodes(cutscene.getTransitions().getValuesAt(interval.getStart()-1));
		}
	}

	@Override
	public void activate(ServerPlayerEntity player, CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		if (config.resetAfterwards && !prevGamemodes.containsKey(player.getUuid())) {
			prevGamemodes.put(player.getUuid(), player.interactionManager.getGameMode());
		}
	}

	@Override
	public void tick(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		cutscene.forAllPlayers(player -> {
			if (player.interactionManager.getGameMode() != config.gamemode) {
				player.changeGameMode(config.gamemode);
			}
		});
	}

	@Override
	public void deactivate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {

	}

	@Override
	public void deactivate(ServerPlayerEntity player, CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		if (config.resetAfterwards && prevGamemodes.containsKey(player.getUuid())) {
			player.changeGameMode(prevGamemodes.get(player.getUuid()));
		}
	}

	@Override
	public TransitionType<?> getType() {
		return TransitionRegistry.SET_GAME_MODE;
	}

	public record Config(GameMode gamemode, boolean resetAfterwards) implements TransitionConfig {

		public static final MapCodec<Config> CODEC = RecordCodecBuilder.mapCodec(
				instance -> instance.group(
						GameMode.CODEC.fieldOf("gamemode").forGetter(Config::gamemode),
						Codec.BOOL.optionalFieldOf("reset_afterwards", true).forGetter(Config::resetAfterwards)
				).apply(instance, Config::new)
		);

		@Override
		public Transition create() {
			return new SetGameModeTransition(this);
		}

		@Override
		public TransitionConfigType<?> getConfigType() {
			return TransitionConfigRegistry.SET_GAME_MODE;
		}
	}
}
