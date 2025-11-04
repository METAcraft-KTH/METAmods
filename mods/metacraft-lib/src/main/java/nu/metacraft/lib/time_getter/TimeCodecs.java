package nu.metacraft.lib.time_getter;

import com.mojang.serialization.Codec;
import nu.metacraft.lib.util.METACodecs;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Function;

public class TimeCodecs {

	public static final Codec<LocalTime> LOCAL_TIME_CODEC = net.minecraft.util.ExtraCodecs.temporalCodec(DateTimeFormatter.ISO_LOCAL_TIME).xmap(
			LocalTime::from, Function.identity()
	);

	public static final Codec<DayOfWeek> DAY_OF_WEEK_CODEC = METACodecs.enumCodec(DayOfWeek.class, true);

}
