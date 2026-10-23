package se.metacraft.config.util.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.resources.Identifier;
import se.metacraft.lib.METAcraftLib;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.BinaryOperator;
import java.util.function.Function;

public class EventWithPhases {

	/**
	 * Runs before all builtin parsers. Might be useful in some edge cases.
	 * Please use {@link DEFAULT} instead if you don't explicitly need this.
	 */
	public static final Identifier PRE = METAcraftLib.getID("pre");
	/**
	 * Used for parsers added by this library. DO NOT USE EXTERNALLY!
	 */
	private static final Identifier INTERNAL = METAcraftLib.getID("internal");
	/**
	 * The default phase, runs after all builtin parsers.
	 */
	public static final Identifier DEFAULT = Event.DEFAULT_PHASE;

	/**
	 * A post phase. Useful if you need to ensure your specific parsers happens after all parsers added by other mods.
	 * Please use {@link DEFAULT} instead if you don't explicitly need this.
	 */
	public static final Identifier POST = METAcraftLib.getID("post");


	public static <T> Event<T> createPrePostEventWithInternal(
		Class<T> clazz, Function<T[], T> invokerFactory,
		T internal
	) {
		var event = EventFactory.createWithPhases(
			clazz, invokerFactory,
			PRE, INTERNAL, DEFAULT, POST
		);
		event.register(INTERNAL, internal);
		return event;
	}


	public static <T, O> Optional<O> getOptionalResult(
		T[] events, Function<T, Optional<O>> applier
	) {
		return Arrays.stream(events).reduce(
			Optional.empty(),
			(lhs, rhs) -> lhs.or(() -> applier.apply(rhs)),
			(lhs, rhs) -> lhs.or(() -> rhs)
		);
	}

	public static <T, O> O getMergedResult(
		T[] events, Function<T, O> applier, BinaryOperator<O> merger, O fallback
	) {
		return Arrays.stream(events).map(applier).reduce(fallback, merger);
	}

}
