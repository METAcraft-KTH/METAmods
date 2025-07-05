package nu.metacraft.cutscenes.transitions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.intprovider.IntProvider;
import nu.metacraft.cutscenes.cutscene.CutsceneInstance;
import nu.metacraft.cutscenes.registry.TransitionConfigRegistry;
import nu.metacraft.cutscenes.registry.TransitionRegistry;
import nu.metacraft.cutscenes.transitions.config.TransitionConfigType;
import nu.metacraft.cutscenes.util.IntervalMap;

import java.util.Optional;
import java.util.function.BiConsumer;

public class SetWeatherTransition extends InstantTransition {

	public static final MapCodec<SetWeatherTransition> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Weather.CODEC.fieldOf("weather").forGetter(t -> t.weather),
					IntProvider.POSITIVE_CODEC.optionalFieldOf("duration").forGetter(t -> t.duration)
			).apply(instance, SetWeatherTransition::new)
	);

	private final Weather weather;
	private final Optional<IntProvider> duration;

	public SetWeatherTransition(Weather weather, Optional<IntProvider> duration) {
		this.weather = weather;
		this.duration = duration;
	}

	@Override
	public void activate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		int duration = this.duration.orElse(weather.getDefaultProvider()).get(cutscene.getRandom());
		weather.activateWeather(cutscene.getCutsceneWorld(), duration);
	}

	@Override
	public TransitionType<?> getType() {
		return TransitionRegistry.SET_WEATHER;
	}

	@Override
	public TransitionConfigType<?> getConfigType() {
		return TransitionConfigRegistry.SET_WEATHER;
	}

	public enum Weather implements StringIdentifiable {
		CLEAR(
				"clear", ServerWorld.CLEAR_WEATHER_DURATION_PROVIDER,
				(world, duration) -> world.setWeather(duration, 0, false, false)
		),
		RAIN(
				"rain", ServerWorld.RAIN_WEATHER_DURATION_PROVIDER,
				(world, duration) -> world.setWeather(0, duration, true, false)
		),
		THUNDER(
				"thunder", ServerWorld.THUNDER_WEATHER_DURATION_PROVIDER,
				(world, duration) -> world.setWeather(0, duration, true, true)
		);

		public static final Codec<Weather> CODEC = StringIdentifiable.createCodec(Weather::values);

		private final String name;
		private final IntProvider defaultProvider;
		private final BiConsumer<ServerWorld, Integer> activator;

		Weather(String name, IntProvider defaultProvider, BiConsumer<ServerWorld, Integer> activator) {
			this.name = name;
			this.defaultProvider = defaultProvider;
			this.activator = activator;
		}

		public IntProvider getDefaultProvider() {
			return defaultProvider;
		}

		@Override
		public String asString() {
			return name;
		}

		public void activateWeather(ServerWorld world, int duration) {
			activator.accept(world, duration);
		}
	}
}
