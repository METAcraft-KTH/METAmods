package nu.metacraft.cutscenes.transitions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleMap;
import nu.metacraft.cutscenes.cutscene.CutsceneInstance;
import nu.metacraft.cutscenes.registry.TransitionConfigRegistry;
import nu.metacraft.cutscenes.registry.TransitionRegistry;
import nu.metacraft.cutscenes.transitions.config.TransitionConfig;
import nu.metacraft.cutscenes.transitions.config.TransitionConfigType;
import nu.metacraft.cutscenes.util.IntervalMap;

import java.util.ArrayList;
import java.util.Optional;

public class SetGameRuleTransition implements Transition {

	public static final MapCodec<SetGameRuleTransition> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Config.CODEC.forGetter(t -> t.config),
					GameRuleMap.CODEC.optionalFieldOf("prev").forGetter(t -> t.prev)
			).apply(instance, SetGameRuleTransition::new)
	);

	private final Config config;
	private Optional<GameRuleMap> prev = Optional.empty();

	public SetGameRuleTransition(Config config) {
		this.config = config;
	}

	public SetGameRuleTransition(Config config, Optional<GameRuleMap> prev) {
		this(config);
		this.prev = prev;
	}

	private static void setRules(CutsceneInstance cutscene, GameRuleMap rules) {
		cutscene.getCutsceneWorld().getGameRules().setAll(
				rules, cutscene.getServer()
		);
	}

	private static <T> void cacheRule(GameRuleMap gameRules, GameRule<T> rule, CutsceneInstance cutscene) {
		gameRules.set(rule, cutscene.getCutsceneWorld().getGameRules().get(rule));
	}

	private void cacheRules(CutsceneInstance cutscene) {
		var rules = GameRuleMap.copyOf(config.rules);
		for (var rule : new ArrayList<>(rules.keySet())) {
			cacheRule(rules, rule, cutscene);
		}
		prev = Optional.of(rules);
	}

	@Override
	public void activate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		if (config.resetAfterwards) {
			cacheRules(cutscene);
		}
		setRules(cutscene, config.rules);
	}

	@Override
	public void tick(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {

	}

	@Override
	public void deactivate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		prev.ifPresent(rules -> setRules(cutscene, rules));
	}

	@Override
	public TransitionType<?> getType() {
		return TransitionRegistry.SET_GAME_RULE;
	}

	public record Config(GameRuleMap rules, boolean resetAfterwards) implements TransitionConfig {

		public static final MapCodec<Config> CODEC = RecordCodecBuilder.mapCodec(
				instance -> instance.group(
						GameRuleMap.CODEC.fieldOf("rules").forGetter(Config::rules),
						Codec.BOOL.optionalFieldOf("reset_afterwards", false).forGetter(t -> t.resetAfterwards)
				).apply(instance, Config::new)
		);

		@Override
		public Transition create() {
			return new SetGameRuleTransition(this);
		}

		@Override
		public TransitionConfigType<?> getConfigType() {
			return TransitionConfigRegistry.SET_GAME_RULE;
		}
	}
}
