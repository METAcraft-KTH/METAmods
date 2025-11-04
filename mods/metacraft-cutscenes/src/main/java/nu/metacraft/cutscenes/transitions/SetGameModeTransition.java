package nu.metacraft.cutscenes.transitions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
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
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

public class SetGameModeTransition implements Transition {

	public static final MapCodec<SetGameModeTransition> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Config.CODEC.forGetter(t -> t.config),
					Codec.unboundedMap(UUIDUtil.STRING_CODEC, GameType.CODEC).fieldOf("prev_gamemodes").forGetter(t -> t.prevGamemodes)
			).apply(instance, SetGameModeTransition::new)
	);

	private final Config config;
	private final Map<UUID, GameType> prevGamemodes;

	public SetGameModeTransition(Config config) {
		this.config = config;
		this.prevGamemodes = new HashMap<>();
	}

	public SetGameModeTransition(Config config, Map<UUID, GameType> prevGamemodes) {
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
	public void activate(ServerPlayer player, CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		if (config.resetAfterwards && !prevGamemodes.containsKey(player.getUUID())) {
			prevGamemodes.put(player.getUUID(), player.gameMode.getGameModeForPlayer());
		}
	}

	@Override
	public void tick(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		cutscene.forAllPlayers(player -> {
			if (player.gameMode.getGameModeForPlayer() != config.gamemode) {
				player.setGameMode(config.gamemode);
			}
		});
	}

	@Override
	public void deactivate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {

	}

	@Override
	public void deactivate(ServerPlayer player, CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		if (config.resetAfterwards && prevGamemodes.containsKey(player.getUUID())) {
			player.setGameMode(prevGamemodes.get(player.getUUID()));
		}
	}

	@Override
	public TransitionType<?> getType() {
		return TransitionRegistry.SET_GAME_MODE;
	}

	public record Config(GameType gamemode, boolean resetAfterwards) implements TransitionConfig {

		public static final MapCodec<Config> CODEC = RecordCodecBuilder.mapCodec(
				instance -> instance.group(
						GameType.CODEC.fieldOf("gamemode").forGetter(Config::gamemode),
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
