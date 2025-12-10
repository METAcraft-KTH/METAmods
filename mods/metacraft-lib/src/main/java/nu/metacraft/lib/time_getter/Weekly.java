package nu.metacraft.lib.time_getter;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.time.*;

public class Weekly implements RegularTimeGetter {

	public static final MapCodec<Weekly> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					TimeCodecs.LOCAL_TIME_CODEC.fieldOf("time").forGetter(weekly -> weekly.time),
					TimeCodecs.DAY_OF_WEEK_CODEC.fieldOf("day").forGetter(weekly -> weekly.dayOfWeek)
			).apply(instance, Weekly::new)
	);

	private final LocalTime time;
	private final DayOfWeek dayOfWeek;

	public Weekly(LocalTime time, DayOfWeek dayOfWeek) {
		this.time = time;
		this.dayOfWeek = dayOfWeek;
	}

	@Override
	public Instant getNextTime(Instant now) {
		LocalDate date = LocalDate.ofInstant(now, ZoneId.systemDefault());
		if (date.getDayOfWeek() != dayOfWeek) {
			int today = date.getDayOfWeek().ordinal();
			int targetDay = dayOfWeek.ordinal();
			if (today > targetDay) {
				today -= DayOfWeek.values().length;
			}
			date = date.plusDays(targetDay - today);
		}
		var offset = ZoneOffset.systemDefault().getRules().getOffset(now);
		var timeOnTargetDay = date.atTime(time).toInstant(offset);
		if (timeOnTargetDay.isAfter(now)) {
			return timeOnTargetDay;
		} else {
			return date.plusDays(7).atTime(time).toInstant(offset);
		}
	}

	@Override
	public RegularTimeGetterType<?> getType() {
		return RegularTimeGetterRegistry.WEEKLY;
	}
}
