package se.metacraft.skins;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static se.metacraft.skins.DefinitionCodecs.GRADIENT;
import static se.metacraft.skins.DefinitionCodecs.NAME;
import static se.metacraft.skins.DefinitionCodecs.dispatch;
import static se.metacraft.skins.DefinitionCodecs.nameFitsGradient;
import static se.metacraft.skins.DefinitionCodecs.optionalField;
import static se.metacraft.skins.DefinitionCodecs.orElse;
import static se.metacraft.skins.DefinitionCodecs.optionalRawField;
import static se.metacraft.skins.DefinitionCodecs.rawField;
import static se.metacraft.skins.DefinitionCodecs.text;
import static se.metacraft.skins.DefinitionCodecs.topText;
import static se.metacraft.skins.DefinitionCodecs.withError;

/**
 * A skin's {@code skin.json5}: its name (as written, formatting codes and all: {@link SkinName}) and
 * the {@link SkinName.Gradient} that may colour it, Kind, the Materials it's limited to (in vanilla order; empty if it fits every one), description,
 * {@link Release}, Perks and Look. {@link #CODEC} reads the Kind first
 * and lets it pick the codec for the rest, which checks everything that depends on the Kind as it
 * reads. Its errors are the reasons a skin is rejected, in words for an admin.
 */
record SkinDefinition(String name, Optional<SkinName.Gradient> gradient, Kind kind, Optional<List<Material>> materials, Optional<String> description, Release release, List<Perk> perks,
						Look look) {
	/**
	 * The Look: the model file of each slot ({@code model}, or a spear's {@code icon} and
	 * {@code in_hand}, in that order), and whether it's a Dyeable Hat.
	 */
	record Look(Map<String, String> models, boolean dyeable, Optional<Integer> color) {}

	/** Unknown Material names that are vanilla's item-name adjectives for a known one. */
	private static final Map<String, String> MATERIAL_HINTS = Map.of("wooden", "wood", "golden", "gold");

	static final Codec<SkinDefinition> CODEC = dispatch(
			rawField("kind", text("field \"kind\" must be a non-empty string").comapFlatMap(Kind::read, Kind::id), "missing field \"kind\""),
			SkinDefinition::kind, SkinDefinition::fields).codec();

	/** Everything but {@code kind}, read for that Kind. */
	private static MapCodec<SkinDefinition> fields(Kind kind) {
		return StrictCodec.map(RecordCodecBuilder.<SkinDefinition>mapCodec(i -> i.group(
				rawField("name", NAME, "missing field \"name\"").forGetter(SkinDefinition::name),
				optionalField("gradient", GRADIENT).forGetter(SkinDefinition::gradient),
				optionalRawField("materials", materials(kind)).forGetter(SkinDefinition::materials),
				optionalRawField("description", topText("description")).forGetter(SkinDefinition::description),
				optionalRawField("released", RELEASED).forGetter(d -> d.release().released()),
				optionalRawField("obtainable", OBTAINABLE).forGetter(d -> Optional.of(d.release().obtainable())),
				// A Look-only skin simply leaves "perks" out.
				optionalRawField("perks", PerkCodecs.perks(kind)).xmap(p -> p.orElse(List.of()), p -> p.isEmpty() ? Optional.<List<Perk>>empty() : Optional.of(p))
						.forGetter(SkinDefinition::perks),
				rawField("look", look(kind), lookShape(kind) + ", and look is missing or not an object").forGetter(SkinDefinition::look)
		).apply(i, (name, gradient, materials, description, released, obtainable, perks, look) ->
				new SkinDefinition(name, gradient, kind, materials, description, new Release(released, obtainable.orElse(true)), perks, look)))
				.validate(d -> nameFitsGradient(d, d.name(), d.gradient())), "kind");
	}

	/** {@code released}: a date, as {@code "2026/10/31"}. */
	private static final Codec<LocalDate> RELEASED = topText("released").comapFlatMap(date -> {
		try {
			return DataResult.success(LocalDate.parse(date, Release.DATE));
		} catch (DateTimeParseException e) {
			return DataResult.error(() -> "field \"released\" must be a date written year/month/day, as \"2026/10/31\", not \"" + date + "\"");
		}
	}, Release.DATE::format);
	private static final Codec<Boolean> OBTAINABLE = withError(Codec.BOOL, "field \"obtainable\" must be true or false");

	/**
	 * {@code materials}: one or more of the Materials the Kind comes in, each once, returned in
	 * vanilla order. A mace or elytra skin takes none at all.
	 */
	private static Codec<List<Material>> materials(Kind kind) {
		List<Material> allowed = Material.of(kind);
		String names = allowed.stream().map(Material::id).collect(Collectors.joining(", "));
		String notNames = "\"materials\" must be a list of names (one or more of: " + names + ")";
		return withError(Codec.STRING.listOf(), notNames).flatXmap(list -> {
			if (allowed.isEmpty()) return DataResult.error(() -> "the " + kind.id() + " Kind has no materials, so it takes no \"materials\"");
			if (list.isEmpty()) return DataResult.error(() -> "\"materials\" is empty: leave it out to fit every material, or list some of: " + names);
			Set<Material> listed = EnumSet.noneOf(Material.class);
			for (String name : list) {
				Optional<Material> material = Material.byId(name);
				if (material.isEmpty()) {
					String hint = MATERIAL_HINTS.containsKey(name) ? "did you mean \"" + MATERIAL_HINTS.get(name) + "\"?" : "one of: " + names;
					return DataResult.error(() -> "unknown material \"" + name + "\" (" + hint + ")");
				}
				if (!allowed.contains(material.get())) {
					return DataResult.error(() -> "\"" + name + "\" isn't a material of the " + kind.id() + " Kind (it comes in: " + names + ")");
				}
				if (!listed.add(material.get())) return DataResult.error(() -> "\"" + name + "\" is listed twice in \"materials\"");
			}
			return DataResult.success(allowed.stream().filter(listed::contains).toList());
		}, list -> DataResult.success(list.stream().map(Material::id).toList()));
	}

	private static String lookShape(Kind kind) {
		return kind.isSpear() ? "a spear takes look: { icon, in_hand }" : "a " + kind.id() + " takes look: { model }";
	}

	/**
	 * {@code look}: the Kind's model slots, each naming a file, {@code dyeable} (true or false,
	 * and only on a helmet skin; leaving it out is false) and {@code color} (a Dyeable Hat's colour
	 * until it's dyed, {@code "#RRGGBB"}; leaving it out shows its painted colours).
	 */
	private static Codec<Look> look(Kind kind) {
		List<String> slots = kind.isSpear() ? List.of("icon", "in_hand") : List.of("model");
		return new Codec<>() {
			@Override
			public <T> DataResult<Pair<Look, T>> decode(DynamicOps<T> ops, T input) {
				Optional<MapLike<T>> look = ops.getMap(input).result();
				if (look.isEmpty()) return DataResult.error(() -> lookShape(kind) + ", and look is missing or not an object");
				List<String> keys = look.get().entries().flatMap(e -> ops.getStringValue(e.getFirst()).result().stream()).toList();
				if (!keys.stream().filter(k -> !k.equals("dyeable") && !k.equals("color")).collect(Collectors.toSet()).equals(Set.copyOf(slots))) {
					return DataResult.error(() -> lookShape(kind) + ", not look: { " + String.join(", ", keys) + " }");
				}
				boolean dyeable = false;
				T written = look.get().get("dyeable");
				if (written != null) {
					if (kind != Kind.HELMET) {
						return DataResult.error(() -> "\"dyeable\" is only for helmet skins (Dyeable Hats), not " + new Fit(kind, Optional.empty()).aOne());
					}
					Optional<Boolean> value = ops.getBooleanValue(written).result();
					if (value.isEmpty()) return DataResult.error(() -> "\"dyeable\" must be true or false");
					dyeable = value.get();
				}
				Optional<Integer> color = Optional.empty();
				T writtenColor = look.get().get("color");
				if (writtenColor != null) {
					if (!dyeable) return DataResult.error(() -> "\"color\" is a Dyeable Hat's colour until it's dyed, so it needs \"dyeable\": true");
					Optional<String> hex = ops.getStringValue(writtenColor).result().filter(c -> c.matches("#[0-9a-fA-F]{6}"));
					if (hex.isEmpty()) return DataResult.error(() -> "\"color\" must be a colour written \"#RRGGBB\"");
					color = Optional.of(Integer.parseInt(hex.get().substring(1), 16));
				}
				Map<String, String> models = new LinkedHashMap<>();
				for (String slot : slots) {
					DataResult<String> file = topText(slot).parse(ops, Optional.ofNullable(look.get().get(slot)).orElse(ops.empty()));
					if (file instanceof DataResult.Error<String> error) return DataResult.error(error.messageSupplier());
					models.put(slot, file.getOrThrow());
				}
				return DataResult.success(Pair.of(new Look(Collections.unmodifiableMap(models), dyeable, color), ops.empty()));
			}

			@Override
			public <T> DataResult<T> encode(Look look, DynamicOps<T> ops, T prefix) {
				RecordBuilder<T> map = ops.mapBuilder();
				look.models().forEach((slot, file) -> map.add(slot, ops.createString(file)));
				if (look.dyeable()) map.add("dyeable", ops.createBoolean(true));
				look.color().ifPresent(c -> map.add("color", ops.createString(String.format("#%06X", c))));
				return map.build(prefix);
			}
		};
	}
}
