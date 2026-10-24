package se.metacraft.config.parser;

import se.metacraft.lib.util.helper.PCollectionsHelper;
import org.pcollections.HashTreePMap;
import org.pcollections.PMap;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

public record MetadataMap(PMap<MetadataKey<?>, Metadata> metadataMap) {

	public static final MetadataMap EMPTY = new MetadataMap(HashTreePMap.empty());

	public static MetadataMap from(Metadata... metadata) {
		return new MetadataMap(
			PCollectionsHelper.collectToMap(
				Arrays.stream(metadata).filter(Objects::nonNull),
				Metadata::key, m -> m
			)
		);
	}

	public <T extends Metadata> T get(MetadataKey<T> metadataKey) {
		//noinspection unchecked
		return (T) metadataMap.get(metadataKey);
	}

	private MetadataMap modify(PMap<MetadataKey<?>, Metadata> newMap) {
		if (newMap == metadataMap) return this;
		return new MetadataMap(newMap);
	}

	public MetadataMap plus(Metadata metadata) {
		return modify(metadataMap.plus(metadata.key(), metadata));
	}

	public MetadataMap plusAll(MetadataMap map) {
		return modify(metadataMap.plusAll(map.metadataMap));
	}

	public MetadataMap plusAll(Collection<Metadata> metadata) {
		var modified = metadataMap;
		for (var meta : metadata) {
			modified = modified.plus(meta.key(), meta);
		}
		return modify(modified);
	}

	public MetadataMap plusAll(Metadata... metadata) {
		return plusAll(Arrays.asList(metadata));
	}

	public MetadataMap minus(MetadataKey<?> key) {
		return modify(metadataMap.minus(key));
	}

	public MetadataMap minus(Metadata metadata) {
		if (metadataMap.get(metadata.key()) == metadata) {
			return minus(metadata.key());
		}
		return this;
	}

	public MetadataMap minusAll(Collection<Metadata> metadata) {
		var modified = metadataMap;
		for (var meta : metadata) {
			if (metadataMap.get(meta.key()) == meta) {
				modified = modified.minus(meta.key());
			}
		}
		return modify(modified);
	}

}
