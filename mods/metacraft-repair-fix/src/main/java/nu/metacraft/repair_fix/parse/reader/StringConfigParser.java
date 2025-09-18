package nu.metacraft.repair_fix.parse.reader;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import nu.metacraft.repair_fix.RepairFix;
import nu.metacraft.repair_fix.parse.ParseBase;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public abstract class StringConfigParser<T, E extends ParseBase<T>> {

	public static final String COMBINE_SYMBOL = "&";
	public static final String SEPARATE_SYMBOL = "|";
	public static final String ADDITIONAL_COST_SYMBOL = "$";

	private final List<E> parseTypes = new ArrayList<>();
	private final Table<T, T, ParseBase.ParseResult<T, E>> cache = HashBasedTable.create();

	private final Supplier<Iterable<? extends String>> configGetter;

	public StringConfigParser(Supplier<Iterable<? extends String>> configGetter) {
		this.configGetter = configGetter;
	}

	public Optional<ParseBase.ParseResult<T, E>> getFirstMatch(T lhs, T rhs) {
		var cached = cache.get(lhs, rhs); //Is this an unnecessary waste of RAM?
		if (cached != null) {
			return Optional.of(cached);
		}
		for (E parse : parseTypes) {
			var result = parse.shouldCombine(lhs, rhs);
			if (result != ParseBase.Result.DEFAULT) {
				var found = new ParseBase.ParseResult<>(parse, result);
				cache.put(lhs, rhs, found);
				return Optional.of(found);
			}
		}
		return Optional.empty();
	}

	public void loadConfig() {
		parseTypes.clear();
		cache.clear();
		for (String line : configGetter.get()) {
			boolean allow = line.contains(COMBINE_SYMBOL);
			String[] split = line.split(
					"\\" + COMBINE_SYMBOL + "|\\" + SEPARATE_SYMBOL + "|\\" + ADDITIONAL_COST_SYMBOL
			);
			if (split.length < 2) {
				RepairFix.getLogger().error("Config entry \"" + line + "\" is invalid, skipping.");
				continue;
			}
			var read = readLine(allow, split);
			if (read != null) {
				parseTypes.add(read);
			} else {
				RepairFix.getLogger().error("Error reading \"" + line + "\", skipping it.");
			}
		}
	}

	protected abstract E readLine(boolean allow, String[] splitLine);

}
