package se.datasektionen.mc.cutscenes.transitions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.GameRules;
import org.apache.commons.lang3.mutable.MutableObject;
import se.datasektionen.mc.cutscenes.cutscene.CutsceneInstance;
import se.datasektionen.mc.cutscenes.mixin.AccessorGameRulesRule;
import se.datasektionen.mc.cutscenes.registry.TransitionConfigRegistry;
import se.datasektionen.mc.cutscenes.registry.TransitionRegistry;
import se.datasektionen.mc.cutscenes.transitions.config.TransitionConfig;
import se.datasektionen.mc.cutscenes.transitions.config.TransitionConfigType;
import se.datasektionen.mc.cutscenes.util.IntervalMap;

import java.util.Optional;

public class SetGameRuleTransition implements Transition {

	public static final MapCodec<SetGameRuleTransition> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Config.CODEC.forGetter(t -> t.config),
					GameRuleEntry.CODEC.codec().optionalFieldOf("prev").forGetter(t -> t.prev)
			).apply(instance, SetGameRuleTransition::new)
	);

	private final Config config;
	private Optional<GameRuleEntry<?>> prev = Optional.empty();

	public SetGameRuleTransition(Config config) {
		this.config = config;
	}

	public SetGameRuleTransition(Config config, Optional<GameRuleEntry<?>> prev) {
		this(config);
		this.prev = prev;
	}

	private static <T extends GameRules.Rule<T>> void setRule(CutsceneInstance cutscene, GameRuleEntry<T> e) {
		cutscene.getCutsceneWorld().getGameRules().get(e.key).setValue((T) e.rule, cutscene.getServer());
	}

	private <T extends GameRules.Rule<T>> void cacheRule(CutsceneInstance cutscene, GameRules.Key<T> key) {
		this.prev = Optional.of(new GameRuleEntry<>(key, cutscene.getCutsceneWorld().getGameRules().get(key)));
	}

	@Override
	public void activate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		if (config.resetAfterwards) {
			cacheRule(cutscene, config.entry.key);
		}
		setRule(cutscene, config.entry);
	}

	@Override
	public void tick(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {

	}

	@Override
	public void deactivate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		prev.ifPresent(prev -> setRule(cutscene, prev));
	}

	@Override
	public TransitionType<?> getType() {
		return TransitionRegistry.SET_GAME_RULE;
	}

	public record Config(GameRuleEntry<?> entry, boolean resetAfterwards) implements TransitionConfig {

		public static final MapCodec<Config> CODEC = RecordCodecBuilder.mapCodec(
				instance -> instance.group(
						GameRuleEntry.CODEC.forGetter(Config::entry),
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

	public record GameRuleEntry<T extends GameRules.Rule<T>>(GameRules.Key<T> key, GameRules.Rule<T> rule) {

		public record Storage(String key, String value) {
			public static final MapCodec<Storage> CODEC = RecordCodecBuilder.mapCodec(
					instance -> instance.group(
							Codec.STRING.fieldOf("key").forGetter(Storage::key),
							Codec.STRING.fieldOf("value").forGetter(Storage::value)
					).apply(instance, GameRuleEntry.Storage::new)
			);
		}

		public static final MapCodec<GameRuleEntry<?>> CODEC = Storage.CODEC.xmap(
				storage -> {
					MutableObject<GameRuleEntry<?>> rule = new MutableObject<>();
					GameRules.accept(new GameRules.Visitor() {
						@Override
						public <T extends GameRules.Rule<T>> void visit(GameRules.Key<T> key, GameRules.Type<T> type) {
							if (key.getName().equals(storage.key)) {
								var r = type.createRule();
								((AccessorGameRulesRule) r).callDeserialize(storage.value);
								rule.setValue(new GameRuleEntry<>(key, r));
							}
						}
					});
					return rule.getValue();
				},
				entry -> new Storage(entry.key.getName(), entry.rule.serialize())
		);
	}
}
