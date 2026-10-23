package se.metacraft.config.parser;

public interface Metadata {
	MetadataKey<?> key();


	static MetadataMap metadataMap(Metadata... metadata) {
		return MetadataMap.from(metadata);
	}

	static MetadataMap noMetadata() {
		return MetadataMap.EMPTY;
	}
}
