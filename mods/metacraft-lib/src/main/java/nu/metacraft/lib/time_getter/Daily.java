package nu.metacraft.lib.time_getter;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.time.*;

public class Daily implements RegularTimeGetter {

	public static final MapCodec<Daily> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					TimeCodecs.LOCAL_TIME_CODEC.fieldOf("time").forGetter(daily -> daily.time)
			).apply(instance, Daily::new)
	);

	private final LocalTime time;

	public Daily(LocalTime time) {
		this.time = time;
	}

	@Override
	public Instant getNextTime(Instant now) {
		LocalDate date = LocalDate.ofInstant(now, ZoneId.systemDefault());
		var offset = ZoneOffset.systemDefault().getRules().getOffset(now);
		var timeToday = date.atTime(time).toInstant(offset);
		if (timeToday.isAfter(now)) {
			return timeToday;
		} else {
			return date.plusDays(1).atTime(time).toInstant(offset);
		}
	}

	@Override
	public RegularTimeGetterType<?> getType() {
		return RegularTimeGetterRegistry.DAILY;
	}
}
