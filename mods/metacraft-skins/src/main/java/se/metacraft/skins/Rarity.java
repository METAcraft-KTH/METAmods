package se.metacraft.skins;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * A skin's tier, {@code rarity} in {@code skin.json5}: it colours the skin's name, bold, and does
 * nothing else (a box's weights are its own). Change a tier's colour here and every skin of it follows.
 */
public enum Rarity {
	COMMON("common", "&f&l"),
	UNCOMMON("uncommon", "&a&l"),
	RARE("rare", "&9&l"),
	LEGENDARY("legendary", "&6&l");

	private final String id;
	/** The formatting codes the name is written after. */
	private final String codes;

	Rarity(String id, String codes) {
		this.id = id;
		this.codes = codes;
	}

	public String id() {
		return id;
	}

	/** {@code name} as written, coloured as this tier. */
	String colour(String name) {
		return codes + name;
	}

	private static final String NAMES = Arrays.stream(values()).map(Rarity::id).collect(Collectors.joining(", "));
	private static final String ERROR = "field \"rarity\" must be one of: " + NAMES;

	static final Codec<Rarity> CODEC = DefinitionCodecs.withError(Codec.STRING, ERROR).comapFlatMap(
			id -> Arrays.stream(values()).filter(r -> r.id.equals(id)).findFirst().map(DataResult::success)
					.orElseGet(() -> DataResult.error(() -> ERROR + ", not \"" + id + "\"")),
			Rarity::id);
}
