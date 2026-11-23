package se.metacraft.skins;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import se.metacraft.config.container.ConfigContainer;
import se.metacraft.config.util.CommentCodec;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * The server-wide settings, {@code config/metacraft-skins.json5} (metacraft-config). Re-read by
 * {@code /itemskins reload} only, never by vanilla {@code /reload}. {@code perks-enabled} off empties every
 * skin's Perks (and their tooltip lines) at once; {@code perks-affect-players} is for event Perks'
 * target effects; {@code mob-hat-chances} is {@link MobHats}' chance outside a cooldown, in percent, for
 * each box a mob can take its hat from ({@link MobHats#BOXES}; a box left out of it gets
 * {@link #MOB_HAT_CHANCE}), which {@code /itemskins spawnchance <box> <percent>} also sets (and saves). Keys with a hyphen must be quoted in the file
 * (Jankson), or the file doesn't parse and the settings before stay.
 */
public record SkinsSettings(boolean perksAffectPlayers, boolean perksEnabled, SortedMap<String, Double> mobHatChances) {
	/** The default chance of each box in {@code mob-hat-chances}: 5%. */
	public static final double MOB_HAT_CHANCE = 5;
	/** The default {@code mob-hat-chances}: {@link #MOB_HAT_CHANCE} for each of {@link MobHats#BOXES}. */
	public static final SortedMap<String, Double> MOB_HAT_CHANCES = sorted(MobHats.BOXES.stream()
			.collect(Collectors.toMap(box -> box, box -> box.equals("classic") ? MOB_HAT_CHANCE : 0)));
	public static final SkinsSettings DEFAULTS = new SkinsSettings(true, true, MOB_HAT_CHANCES);

	public SkinsSettings {
		mobHatChances = sorted(mobHatChances);
	}

	/** An unchangeable copy, by box id. */
	private static SortedMap<String, Double> sorted(Map<String, Double> chances) {
		return Collections.unmodifiableSortedMap(new TreeMap<>(chances));
	}

	/** A chance in percent, 0 to 100. */
	public static final Codec<Double> PERCENT = Codec.DOUBLE.validate(p -> p >= 0 && p <= 100
			? DataResult.success(p) : DataResult.error(() -> "must be a percentage from 0 to 100"));

	public static final MapCodec<SkinsSettings> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			CommentCodec.comment(
					Codec.BOOL.optionalFieldOf("perks-affect-players", true),
					"Whether Perks that hit a target (on hit, on kill, when hurt) affect players too. Off: mobs only."
			).forGetter(SkinsSettings::perksAffectPlayers),
			CommentCodec.comment(
					Codec.BOOL.optionalFieldOf("perks-enabled", true),
					"Off turns every Perk off; Looks keep working."
			).forGetter(SkinsSettings::perksEnabled),
			CommentCodec.comment(
					Codec.unboundedMap(Codec.STRING, PERCENT).optionalFieldOf("mob-hat-chances", (Map<String, Double>) MOB_HAT_CHANCES)
							.xmap(SkinsSettings::sorted, m -> m),
					"For each Skin Box, the chance in percent (0 to 100) that a mob that would wear one of its hats spawns wearing one. "
							+ "classic: a zombie, husk, zombie villager, skeleton, stray, bogged, parched or creeper; winter: a zombie, "
							+ "skeleton, stray or creeper in a cold biome. A box left out gets 5%. For 1 to 6 hours after a mob spawns wearing a hat, "
							+ "it's 99% less in that chunk and the 8 around it, unless that mob despawns. /itemskins spawnchance sets these too."
			).forGetter(SkinsSettings::mobHatChances)
	).apply(instance, SkinsSettings::new));

	/** The chance, in percent, a mob that would wear a hat from {@code box} puts one on (outside a cooldown). */
	public double mobHatChance(String box) {
		return mobHatChances.getOrDefault(box, MOB_HAT_CHANCE);
	}

	/** These settings with another chance for {@code box}. */
	public SkinsSettings withMobHatChance(String box, double percent) {
		Map<String, Double> chances = new TreeMap<>(mobHatChances);
		chances.put(box, percent);
		return new SkinsSettings(perksAffectPlayers, perksEnabled, sorted(chances));
	}

	/** The settings file of one server root; reloads only when asked. */
	static ConfigContainer<SkinsSettings> container(Path file) {
		return ConfigContainer.Builder.create(CODEC, () -> DEFAULTS).withPath(file).build(MetacraftSkins.MOD_ID);
	}
}
