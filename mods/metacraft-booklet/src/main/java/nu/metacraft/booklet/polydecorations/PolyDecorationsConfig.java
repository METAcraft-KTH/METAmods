package nu.metacraft.booklet.polydecorations;

import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import se.metacraft.config.container.ConfigContainer;
import se.metacraft.config.util.CommentCodec;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Our PolyDecorations fork's feature switches ({@code config/polydecorations.json}) as a METAcraft
 * config: Season 6's choices by default, editable with {@code /meta-config-screen polydecorations}.
 * PolyDecorations reads the file once, as the server starts, so a change applies at the next restart.
 * The file stays in the shape PolyDecorations reads: {@code {"features": {"<feature>": true | false | "hard"}}}.
 */
public record PolyDecorationsConfig(Map<String, Feature> features) {

	/** A feature's switch, as PolyDecorations reads it. */
	public enum Feature {
		/** On. */
		ON,
		/** Registered, but nothing to craft or find in survival. */
		OFF,
		/** Not registered at all: no blocks, items or resource-pack assets. */
		HARD;

		public static final Codec<Feature> CODEC = Codec.either(Codec.BOOL, Codec.STRING).comapFlatMap(
				value -> value.map(
						on -> DataResult.success(on ? ON : OFF),
						text -> switch (text) {
							case "true" -> DataResult.success(ON);
							case "false" -> DataResult.success(OFF);
							case "hard" -> DataResult.success(HARD);
							default -> DataResult.error(() -> "expected true, false or \"hard\", not \"" + text + "\"");
						}
				),
				feature -> feature == HARD ? Either.right("hard") : Either.left(feature == ON)
		);
	}

	public static final MapCodec<PolyDecorationsConfig> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			CommentCodec.comment(
					Codec.unboundedMap(Codec.STRING, Feature.CODEC).fieldOf("features"),
					"Each PolyDecorations feature: true (on), false (registered, but nothing to craft or find),",
					"or \"hard\" (not registered at all). Applies at the next restart. Season 6 keeps the canvas,",
					"mailboxes, rope, sign posts, the hammer and trowel, wall lanterns and fence leads."
			).forGetter(PolyDecorationsConfig::features)
	).apply(instance, PolyDecorationsConfig::new));

	/** Season 6's features, from the file {@link S6Defaults} writes on a first start. */
	public static final PolyDecorationsConfig S6 = readS6();

	private static final ConfigContainer<PolyDecorationsConfig> CONTAINER = ConfigContainer.Builder.create(
			CODEC, () -> S6
	).withPath(S6Defaults.path()).build("polydecorations");

	/** Registers the config with metacraft-config; only when PolyDecorations is loaded. */
	public static void init() {
		CONTAINER.get();
	}

	public static PolyDecorationsConfig get() {
		return CONTAINER.get();
	}

	private static PolyDecorationsConfig readS6() {
		try (var in = new InputStreamReader(PolyDecorationsConfig.class.getResourceAsStream(S6Defaults.RESOURCE), StandardCharsets.UTF_8)) {
			var features = new LinkedHashMap<String, Feature>();
			for (var entry : JsonParser.parseReader(in).getAsJsonObject().getAsJsonObject("features").entrySet()) {
				var value = entry.getValue().getAsJsonPrimitive();
				features.put(entry.getKey(), value.isBoolean() ? (value.getAsBoolean() ? Feature.ON : Feature.OFF)
						: "hard".equals(value.getAsString()) ? Feature.HARD : Feature.ON);
			}
			return new PolyDecorationsConfig(features);
		} catch (Exception e) {
			throw new IllegalStateException("metacraft-booklet: " + S6Defaults.RESOURCE + " is missing or broken", e);
		}
	}

}
