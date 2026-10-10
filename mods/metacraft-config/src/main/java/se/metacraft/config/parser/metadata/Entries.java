package se.metacraft.config.parser.metadata;

import com.mojang.serialization.Codec;
import net.minecraft.core.HolderLookup;
import nu.metacraft.lib.util.helper.PCollectionsHelper;
import org.pcollections.PSet;
import org.pcollections.TreePSet;
import se.metacraft.config.parser.Metadata;
import se.metacraft.config.parser.MetadataKey;
import se.metacraft.config.parser.CodecParser;

import java.util.Comparator;
import java.util.Set;
import java.util.stream.Stream;

public record Entries(PSet<Entry> strings) implements Metadata {
	public static final PSet<Entry> EMPTY_SET = TreePSet.empty(Comparator.comparing(Entry::key));
	public static Entries from(Stream<Entry> stream) {
		return new Entries(PCollectionsHelper.collect(stream, EMPTY_SET));
	}

	public boolean isEmpty() {
		return strings.isEmpty();
	}

	@Override
	public MetadataKey<Entries> key() {
		return MetadataKey.ENTRIES;
	}

	public record Entry(String key, Object value) {
		public Entry withValue(Object value) {
			return new Entry(key, value);
		}
	}

	public static Set<Entry> getEntries(Codec<?> codec, HolderLookup.Provider lookup) {
		var parsed = CodecParser.parse(codec, lookup);
		for (var entry : parsed.thisAndAllUnderlying()) {
			var entries = entry.metadata(MetadataKey.ENTRIES);
			if (entries.isPresent()) {
				return entries.get().strings();
			}
		}
		return Set.of();
	}
}
