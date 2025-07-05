package nu.metacraft.lib.condition.conditions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.loot.condition.LootCondition;
import net.minecraft.loot.condition.LootConditionType;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.context.ContextParameter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.biome.Biome;
import nu.metacraft.lib.condition.METAcraftConditions;

import java.util.Set;
import java.util.function.BiPredicate;

public class LocalWeather implements LootCondition {

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
	public LootConditionType getType() {
		return METAcraftConditions.LOCAL_WEATHER;
	}

	@Override
	public boolean test(LootContext context) {
		var pos = context.get(LootContextParameters.ORIGIN);
		if (pos == null) return false;
		return weather.isActive(context.getWorld(), BlockPos.ofFloored(pos));
	}

	@Override
	public Set<ContextParameter<?>> getAllowedParameters() {
		return Set.of(LootContextParameters.ORIGIN);
	}

	public enum WeatherType implements StringIdentifiable {
		CLEAR("clear", (world, pos) ->
				!world.getLevelProperties().isRaining() ||
				world.getBiome(pos).value().getPrecipitation(pos, world.getSeaLevel()) == Biome.Precipitation.NONE
		),
		RAIN("rain", (world, pos) ->
				world.getLevelProperties().isRaining() &&
				world.getBiome(pos).value().getPrecipitation(pos, world.getSeaLevel()) == Biome.Precipitation.RAIN
		),
		SNOW("snow", (world, pos) ->
				world.getLevelProperties().isRaining() &&
				world.getBiome(pos).value().getPrecipitation(pos, world.getSeaLevel()) == Biome.Precipitation.SNOW
		),
		THUNDER("thunder", (world, pos) ->
				world.getLevelProperties().isThundering() &&
				world.getBiome(pos).value().getPrecipitation(pos, world.getSeaLevel()) == Biome.Precipitation.RAIN
		);

		public static final Codec<WeatherType> CODEC = StringIdentifiable.createCodec(WeatherType::values);

		private final String name;
		private final BiPredicate<ServerWorldAccess, BlockPos> isActive;

		WeatherType(String name, BiPredicate<ServerWorldAccess, BlockPos> isActive) {
			this.name = name;
			this.isActive = isActive;
		}

		@Override
		public String asString() {
			return name;
		}

		public boolean isActive(ServerWorldAccess world, BlockPos pos) {
			return isActive.test(world, pos);
		}
	}
}
