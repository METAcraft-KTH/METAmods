package se.datasektionen.mc.metacraft_lib.time_getter;

import com.mojang.serialization.Codec;
import net.minecraft.util.dynamic.Codecs;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Function;

public class TimeCodecs {

	public static final Codec<LocalTime> LOCAL_TIME_CODEC = Codecs.formattedTime(DateTimeFormatter.ISO_LOCAL_TIME).xmap(
			LocalTime::from, Function.identity()
	);

}
