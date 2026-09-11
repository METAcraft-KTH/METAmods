package nu.metacraft.lib.condition.conditions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import nu.metacraft.lib.condition.METAcraftConditions;

import java.util.Set;
import java.util.function.BiPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class LocalWeather implements LootItemCondition {

	public static final MapCodec<LocalWeather> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
						WeatherType.CODEC.fieldOf("weather").forGetter(weather -> weather.weather)
			).apply(instance, LocalWeather::new)
	);

	private final WeatherType weather;

	public LocalWeather(WeatherType weather) {
		this.weather = weather;
	}

	@Override
	public MapCodec<? extends LootItemCondition> codec() {
		return METAcraftConditions.LOCAL_WEATHER;
	}

	@Override
	public boolean test(LootContext context) {
		var pos = context.getOptional(LootContextParams.ORIGIN);
		if (pos == null) return false;
		return weather.isActive(context.getLevel(), BlockPos.containing(pos));
	}

	@Override
	public Set<ContextKey<?>> getReferencedContextParams() {
		return Set.of(LootContextParams.ORIGIN);
	}

	public enum WeatherType implements StringRepresentable {
		CLEAR("clear", (world, pos) ->
				!world.getWeatherData().isRaining() ||
				world.getBiome(pos).value().getPrecipitationAt(pos, world.getSeaLevel()) == Biome.Precipitation.NONE
		),
		RAIN("rain", (world, pos) ->
				world.getWeatherData().isRaining() &&
				world.getBiome(pos).value().getPrecipitationAt(pos, world.getSeaLevel()) == Biome.Precipitation.RAIN
		),
		SNOW("snow", (world, pos) ->
				world.getWeatherData().isRaining() &&
				world.getBiome(pos).value().getPrecipitationAt(pos, world.getSeaLevel()) == Biome.Precipitation.SNOW
		),
		THUNDER("thunder", (world, pos) ->
				world.getWeatherData().isThundering() &&
				world.getBiome(pos).value().getPrecipitationAt(pos, world.getSeaLevel()) == Biome.Precipitation.RAIN
		);

		public static final Codec<WeatherType> CODEC = StringRepresentable.fromEnum(WeatherType::values);

		private final String name;
		private final BiPredicate<ServerLevel, BlockPos> isActive;

		WeatherType(String name, BiPredicate<ServerLevel, BlockPos> isActive) {
			this.name = name;
			this.isActive = isActive;
		}

		@Override
		public String getSerializedName() {
			return name;
		}

		public boolean isActive(ServerLevel world, BlockPos pos) {
			return isActive.test(world, pos);
		}
	}
}
