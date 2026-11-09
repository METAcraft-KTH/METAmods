package se.metacraft.skins;

import blue.endless.jankson.Jankson;
import blue.endless.jankson.JsonObject;
import blue.endless.jankson.api.SyntaxError;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import se.metacraft.config.util.JanksonOps;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import static se.metacraft.skins.DefinitionCodecs.GRADIENT;
import static se.metacraft.skins.DefinitionCodecs.NAME;
import static se.metacraft.skins.DefinitionCodecs.WHOLE;
import static se.metacraft.skins.DefinitionCodecs.list;
import static se.metacraft.skins.DefinitionCodecs.nameFitsGradient;
import static se.metacraft.skins.DefinitionCodecs.object;
import static se.metacraft.skins.DefinitionCodecs.optionalField;
import static se.metacraft.skins.DefinitionCodecs.optionalRawField;
import static se.metacraft.skins.DefinitionCodecs.rawField;
import static se.metacraft.skins.DefinitionCodecs.topText;
import static se.metacraft.skins.DefinitionCodecs.withError;

/**
 * Reads one Skin Box file, {@code boxes/<id>.json5}: its name (with formatting codes, and the
 * gradient that may colour it, as a skin's), description, and its skins with their weights, in
 * file order. {@code ominous} is still read (true or false), so files from when it drew a box as the
 * ominous vault still load, and ignored: every box looks the same. Read as strictly as {@code skin.json5}: anything
 * wrong rejects the box with the reason, in the same words. Whether each skin is on the server is
 * the reload's to say ({@link Skins#reload}).
 */
final class BoxFile {
	private BoxFile() {}

	static final String SUFFIX = ".json5";
	static final int MAX_WEIGHT = 1_000_000;

	private static final Jankson JANKSON = Jankson.builder().build();
	/** What an id may hold, as a skin id. */
	private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+");

	/** One skin a box holds, and its weight. */
	record Entry(String skin, int weight) {}

	/** A box file that loaded. {@code name} is the name without its formatting codes, {@code title} the name with them. */
	record Loaded(String id, String name, Component title, Optional<String> description, List<Entry> skins) {}

	private record Definition(String name, Optional<SkinName.Gradient> gradient, Optional<String> description, Optional<Boolean> ominous, List<Entry> skins) {}

	private static final String WEIGHTS = "must be a whole number from 1 to 1,000,000";
	private static final Codec<Integer> WEIGHT = withError(WHOLE, WEIGHTS).comapFlatMap(
			w -> w >= 1 && w <= MAX_WEIGHT ? DataResult.success(w.intValue()) : DataResult.error(() -> WEIGHTS), Integer::longValue);
	private static final Codec<Entry> ENTRY = object(StrictCodec.of(RecordCodecBuilder.<Entry>mapCodec(i -> i.group(
			rawField("skin", topText("skin"), "missing field \"skin\"").forGetter(Entry::skin),
			rawField("weight", withError(WEIGHT, "field \"weight\" " + WEIGHTS), "missing field \"weight\"").forGetter(Entry::weight)
	).apply(i, Entry::new))), "must be an object: { skin: \"<skin id>\", weight: <n> }");
	private static final Codec<List<Entry>> SKINS = list("skins", ENTRY, "\"skins\" must be a list of { skin: \"<skin id>\", weight: <n> }").validate(skins -> {
		if (skins.isEmpty()) return DataResult.error(() -> "\"skins\" is empty: a box needs at least one skin");
		Set<String> seen = new HashSet<>();
		for (Entry entry : skins) {
			if (!seen.add(entry.skin())) return DataResult.error(() -> "\"" + entry.skin() + "\" is listed twice in \"skins\"");
		}
		return DataResult.success(skins);
	});
	private static final Codec<Definition> CODEC = StrictCodec.of(RecordCodecBuilder.<Definition>mapCodec(i -> i.group(
			rawField("name", NAME, "missing field \"name\"").forGetter(Definition::name),
			optionalField("gradient", GRADIENT).forGetter(Definition::gradient),
			optionalRawField("description", topText("description")).forGetter(Definition::description),
			optionalRawField("ominous", withError(Codec.BOOL, "field \"ominous\" must be true or false")).forGetter(Definition::ominous),
			rawField("skins", SKINS, "missing field \"skins\"").forGetter(Definition::skins)
	).apply(i, Definition::new))
			.validate(d -> nameFitsGradient(d, d.name(), d.gradient())));

	/** The box id a file in {@code boxes/} stands for: its name without {@code .json5}. */
	static String id(Path file) {
		String name = file.getFileName().toString();
		return name.substring(0, name.length() - SUFFIX.length());
	}

	static Loaded load(Path file) throws SkinFolder.Rejected {
		String id = id(file);
		if (!ID.matcher(id).matches()) throw new SkinFolder.Rejected(file, "the file name is the box id and may only hold a-z, 0-9, _, - and .");
		JsonObject json;
		try {
			json = JANKSON.load(Files.readString(file, StandardCharsets.UTF_8));
		} catch (SyntaxError e) {
			throw new SkinFolder.Rejected(file, "broken json5: " + e.getCompleteMessage());
		} catch (IOException | RuntimeException e) {
			throw new SkinFolder.Rejected(file, "broken json5: " + e.getMessage());
		}
		if (json == null) throw new SkinFolder.Rejected(file, "broken json5: not an object");
		Definition box = CODEC.parse(JanksonOps.INSTANCE, json).getOrThrow(reason -> new SkinFolder.Rejected(file, reason));
		return new Loaded(id, SkinName.plain(box.name()), SkinName.styled(box.name(), box.gradient()), box.description(), box.skins());
	}
}
