package nu.metacraft.cutscenes.transitions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.valueproviders.IntProviders;
import net.minecraft.world.level.saveddata.WeatherData;
import nu.metacraft.cutscenes.cutscene.CutsceneInstance;
import nu.metacraft.cutscenes.registry.TransitionConfigRegistry;
import nu.metacraft.cutscenes.registry.TransitionRegistry;
import nu.metacraft.cutscenes.transitions.config.TransitionConfigType;
import nu.metacraft.cutscenes.util.IntervalMap;

import java.util.Optional;
import java.util.function.BiConsumer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.valueproviders.IntProvider;

public class SetWeatherTransition extends InstantTransition {

	public static final MapCodec<SetWeatherTransition> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Weather.CODEC.fieldOf("weather").forGetter(t -> t.weather),
					IntProviders.POSITIVE_CODEC.optionalFieldOf("duration").forGetter(t -> t.duration)
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
		int duration = this.duration.orElse(weather.getDefaultProvider()).sample(cutscene.getRandom());
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

	public enum Weather implements StringRepresentable {
		CLEAR(
				"clear", ServerLevel.RAIN_DELAY,
				(world, duration) -> setWeatherParameters(world.getWeatherData(), duration, 0, false, false)
		),
		RAIN(
				"rain", ServerLevel.RAIN_DURATION,
				(world, duration) -> setWeatherParameters(world.getWeatherData(), 0, duration, true, false)
		),
		THUNDER(
				"thunder", ServerLevel.THUNDER_DURATION,
				(world, duration) -> setWeatherParameters(world.getWeatherData(), 0, duration, true, true)
		);

		private static void setWeatherParameters(
				WeatherData weatherData,
				final int clearTime, final int rainTime, final boolean raining, final boolean thundering
		) {
			weatherData.setClearWeatherTime(clearTime);
			weatherData.setRainTime(rainTime);
			weatherData.setThunderTime(rainTime);
			weatherData.setRaining(raining);
			weatherData.setThundering(thundering);
		}

		public static final Codec<Weather> CODEC = StringRepresentable.fromEnum(Weather::values);

		private final String name;
		private final IntProvider defaultProvider;
		private final BiConsumer<ServerLevel, Integer> activator;

		Weather(String name, IntProvider defaultProvider, BiConsumer<ServerLevel, Integer> activator) {
			this.name = name;
			this.defaultProvider = defaultProvider;
			this.activator = activator;
		}

		public IntProvider getDefaultProvider() {
			return defaultProvider;
		}

		@Override
		public String getSerializedName() {
			return name;
		}

		public void activateWeather(ServerLevel world, int duration) {
			activator.accept(world, duration);
		}
	}
}
