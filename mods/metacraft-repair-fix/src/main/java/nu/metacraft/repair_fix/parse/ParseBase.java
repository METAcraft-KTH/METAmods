package nu.metacraft.repair_fix.parse;

import java.util.function.Predicate;
import java.util.function.Supplier;

public class ParseBase<T> {

	private final Predicate<T> firstCheck, secondCheck;
	private final boolean combine;

	public ParseBase(
			Predicate<T> firstCheck,
			Predicate<T> secondCheck,
			boolean combine
	) {
		this.firstCheck = firstCheck;
		this.secondCheck = secondCheck;
		this.combine = combine;
	}

	public Result shouldCombine(T lhs, T rhs) {
		if (
				(firstCheck.test(lhs) && secondCheck.test(rhs)) ||
				(firstCheck.test(rhs) && secondCheck.test(lhs))
		) {
			if (combine) {
				return Result.ALLOW;
			} else {
				return Result.DENY;
			}
		}
		return Result.DEFAULT;
	}

	public record ParseResult<T, E extends ParseBase<T>>(E parse, Result result) {}

	public enum Result {
		DENY(() -> false),
		DEFAULT(() -> {
			throw new IllegalStateException("There should never be a situation where you call shouldAllow for default!");
		}),
		ALLOW(() -> true);

		private final Supplier<Boolean> shouldAllow;

		Result(Supplier<Boolean> shouldAllow) {
			this.shouldAllow = shouldAllow;
		}

		public boolean shouldAllow() {
			return shouldAllow.get();
		}
	}

}
