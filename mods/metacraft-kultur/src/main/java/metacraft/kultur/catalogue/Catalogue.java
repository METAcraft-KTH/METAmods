package metacraft.kultur.catalogue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The one list everything else is derived from: {@code kultur.json} in the mod's resources. Datagen
 * reads it to write the data and assets; the server reads it to register the pattern items. A
 * chapter is a folder of art and a group in the catalogue, nothing more — a Data or Media student
 * adds their entries here and their PNGs under {@code art/kultur/<chapter>/}.
 */
public record Catalogue(List<Chapter> chapters) {
	private static final java.util.regex.Pattern ID = java.util.regex.Pattern.compile("[a-z0-9_]+");

	/** A banner pattern; {@code item} means the loom wants a pattern item for it (the vanilla globe / flower mechanic). */
	public record Pattern(String id, String name, boolean item) {
		public static final Codec<Pattern> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("id").forGetter(Pattern::id),
				Codec.STRING.fieldOf("name").forGetter(Pattern::name),
				Codec.BOOL.optionalFieldOf("item", false).forGetter(Pattern::item)
		).apply(i, Pattern::new));

		/** The pattern item's registry path, when {@link #item}. */
		public String itemPath() {
			return id + "_banner_pattern";
		}
	}

	/** A painting variant; {@code width}/{@code height} in blocks, the PNG is 16 px per block. */
	public record Painting(String id, String title, String author, int width, int height) {
		public static final Codec<Painting> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("id").forGetter(Painting::id),
				Codec.STRING.fieldOf("title").forGetter(Painting::title),
				Codec.STRING.fieldOf("author").forGetter(Painting::author),
				Codec.INT.fieldOf("width").forGetter(Painting::width),
				Codec.INT.fieldOf("height").forGetter(Painting::height)
		).apply(i, Painting::new));
	}

	public record Chapter(String id, String name, List<Pattern> patterns, List<Painting> paintings) {
		public static final Codec<Chapter> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("id").forGetter(Chapter::id),
				Codec.STRING.fieldOf("name").forGetter(Chapter::name),
				Pattern.CODEC.listOf().optionalFieldOf("patterns", List.of()).forGetter(Chapter::patterns),
				Painting.CODEC.listOf().optionalFieldOf("paintings", List.of()).forGetter(Chapter::paintings)
		).apply(i, Chapter::new));
	}

	/** An entry with the chapter it belongs to — the chapter names the art folder. */
	public record Owned<T>(Chapter chapter, T value) {}

	public static final Codec<Catalogue> CODEC = RecordCodecBuilder.create(i -> i.group(
			Chapter.CODEC.listOf().fieldOf("chapters").forGetter(Catalogue::chapters)
	).apply(i, Catalogue::new));

	/** The shipped catalogue, validated. */
	public static Catalogue load() {
		try (InputStream in = Catalogue.class.getResourceAsStream("/kultur.json")) {
			if (in == null) throw new IllegalStateException("kultur.json is missing from the jar");
			return parse(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** Parses and validates; every complaint names the entry so a contributor can find it. */
	public static Catalogue parse(JsonElement json) {
		Catalogue c = CODEC.parse(JsonOps.INSTANCE, json)
				.getOrThrow(msg -> new IllegalStateException("kultur.json: " + msg));
		Set<String> chapterIds = new HashSet<>(), patternIds = new HashSet<>(), paintingIds = new HashSet<>();
		for (Chapter chapter : c.chapters) {
			id(chapter.id, "chapter");
			if (!chapterIds.add(chapter.id)) throw new IllegalStateException("kultur.json: chapter id '" + chapter.id + "' twice");
			for (Pattern p : chapter.patterns) {
				id(p.id, "pattern in chapter " + chapter.id);
				if (!patternIds.add(p.id)) throw new IllegalStateException("kultur.json: pattern id '" + p.id + "' twice");
				if (p.name.isBlank()) throw new IllegalStateException("kultur.json: pattern '" + p.id + "' has a blank name");
			}
			for (Painting q : chapter.paintings) {
				id(q.id, "painting in chapter " + chapter.id);
				if (!paintingIds.add(q.id)) throw new IllegalStateException("kultur.json: painting id '" + q.id + "' twice");
				if (q.width < 1 || q.height < 1) throw new IllegalStateException("kultur.json: painting '" + q.id + "' is " + q.width + "×" + q.height + " blocks");
				if (q.title.isBlank()) throw new IllegalStateException("kultur.json: painting '" + q.id + "' has a blank title");
				if (q.author.isBlank()) throw new IllegalStateException("kultur.json: painting '" + q.id + "' has a blank author");
			}
		}
		return c;
	}

	private static void id(String id, String what) {
		if (!ID.matcher(id).matches()) throw new IllegalStateException("kultur.json: " + what + " id '" + id + "' is not [a-z0-9_]+");
	}

	public List<Owned<Pattern>> patterns() {
		List<Owned<Pattern>> out = new ArrayList<>();
		for (Chapter chapter : chapters) for (Pattern p : chapter.patterns) out.add(new Owned<>(chapter, p));
		return out;
	}

	public List<Owned<Painting>> paintings() {
		List<Owned<Painting>> out = new ArrayList<>();
		for (Chapter chapter : chapters) for (Painting q : chapter.paintings) out.add(new Owned<>(chapter, q));
		return out;
	}
}
