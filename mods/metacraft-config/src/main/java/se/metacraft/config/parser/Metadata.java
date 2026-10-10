package se.metacraft.config.parser;

import nu.metacraft.lib.util.helper.PCollectionsHelper;
import org.pcollections.HashTreePMap;
import org.pcollections.PMap;

import java.util.Arrays;
import java.util.Objects;

public interface Metadata {
	MetadataKey<?> key();


	static MetadataMap metadataMap(Metadata... metadata) {
		return MetadataMap.from(metadata);
	}

	static MetadataMap noMetadata() {
		return MetadataMap.EMPTY;
	}
}
