package nu.metacraft.cutscenes.transitions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.apache.commons.lang3.mutable.MutableObject;
import nu.metacraft.cutscenes.Cutscenes;
import nu.metacraft.cutscenes.cutscene.CutsceneInstance;
import nu.metacraft.cutscenes.mixin.AccessorGameRulesRule;
import nu.metacraft.cutscenes.registry.TransitionConfigRegistry;
import nu.metacraft.cutscenes.registry.TransitionRegistry;
import nu.metacraft.cutscenes.transitions.config.TransitionConfig;
import nu.metacraft.cutscenes.transitions.config.TransitionConfigType;
import nu.metacraft.cutscenes.util.IntervalMap;

import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;

public class SetGameRuleTransition implements Transition {

	public static final MapCodec<SetGameRuleTransition> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Config.CODEC.forGetter(t -> t.config),
					GameRuleEntry.CODEC.codec().optionalFieldOf("prev").forGetter(t -> t.prev)
			).apply(instance, SetGameRuleTransition::new)
	);

	private final Config config;
	private Optional<GameRuleEntry> prev = Optional.empty();
	private Optional<GameRuleEntry.Parsed<?>> prevReady = Optional.empty();

	public SetGameRuleTransition(Config config) {
		this.config = config;
	}

	public SetGameRuleTransition(Config config, Optional<GameRuleEntry> prev) {
		this(config);
		this.prev = prev;
	}

	private static <T extends GameRules.Value<T>> void setRule(CutsceneInstance cutscene, GameRuleEntry.Parsed<T> e) {
		cutscene.getCutsceneWorld().getGameRules().getRule(e.key).setFrom((T) e.rule, cutscene.getServer());
	}

	private <T extends GameRules.Value<T>> void cacheRule(CutsceneInstance cutscene, GameRules.Key<T> key) {
		this.prevReady = Optional.of(
				new GameRuleEntry.Parsed<>(
						key, cutscene.getCutsceneWorld().getGameRules().getRule(key)
				)
		);
		this.prev = prevReady.map(GameRuleEntry.Parsed::serialize);
	}

	private void error(String key) {
		Cutscenes.LOGGER.error("The game rule " + key + " does not exist!");
	}

	@Override
	public void activate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		config.entry.parse(cutscene.getCutsceneWorld()).ifPresentOrElse(parsed -> {
			if (config.resetAfterwards) {
				cacheRule(cutscene, parsed.key);
			}
			setRule(cutscene, parsed);
		}, () -> {
			error(config.entry.key);
		});
	}

	@Override
	public void tick(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {

	}

	@Override
	public void deactivate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		prevReady.or(() -> prev.flatMap(
				prev -> prev.parse(cutscene.getCutsceneWorld())
		)).ifPresent(prev -> setRule(cutscene, prev));
	}

	@Override
	public TransitionType<?> getType() {
		return TransitionRegistry.SET_GAME_RULE;
	}

	public record Config(GameRuleEntry entry, boolean resetAfterwards) implements TransitionConfig {

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

	public record GameRuleEntry(String key, String value) {

		public static final MapCodec<GameRuleEntry> CODEC = RecordCodecBuilder.mapCodec(
				instance -> instance.group(
						Codec.STRING.fieldOf("key").forGetter(GameRuleEntry::key),
						Codec.STRING.fieldOf("value").forGetter(GameRuleEntry::value)
				).apply(instance, GameRuleEntry::new)
		);

		public Optional<Parsed<?>> parse(ServerLevel world) {
			MutableObject<GameRuleEntry.Parsed<?>> rule = new MutableObject<>();
			world.getGameRules().visitGameRuleTypes(new GameRules.GameRuleTypeVisitor() {
				@Override
				public <T extends GameRules.Value<T>> void visit(GameRules.Key<T> key, GameRules.Type<T> type) {
					if (key.getId().equals(GameRuleEntry.this.key)) {
						var r = type.createRule();
						((AccessorGameRulesRule) r).callDeserialize(value);
						rule.setValue(new GameRuleEntry.Parsed<>(key, r));
					}
				}
			});
			return Optional.ofNullable(rule.getValue());
		}

		public record Parsed<T extends GameRules.Value<T>>(GameRules.Key<T> key, GameRules.Value<T> rule) {
			public GameRuleEntry serialize() {
				return new GameRuleEntry(key.getId(), rule.serialize());
			}
		}
	}
}
