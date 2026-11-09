package se.metacraft.skins;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import se.metacraft.config.container.ConfigContainer;
import se.metacraft.config.util.CommentCodec;

import java.nio.file.Path;

/**
 * The server-wide settings, {@code config/metacraft-skins.json5} (metacraft-config). Re-read by
 * {@code /itemskins reload} only, never by vanilla {@code /reload}. {@code perks-enabled} off empties every
 * skin's Perks (and their tooltip lines) at once; {@code perks-affect-players} is for event Perks'
 * target effects; {@code mob-hat-chance} is {@link MobHats}' chance at local difficulty 0, in percent,
 * which {@code /itemskins spawnchance} also sets (and saves). Keys with a hyphen must be quoted in the file
 * (Jankson), or the file doesn't parse and the settings before stay.
 */
public record SkinsSettings(boolean perksAffectPlayers, boolean perksEnabled, double mobHatChance) {
	/** The default {@code mob-hat-chance}: 0.2%. */
	public static final double MOB_HAT_CHANCE = 0.2;
	public static final SkinsSettings DEFAULTS = new SkinsSettings(true, true, MOB_HAT_CHANCE);

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
					PERCENT.optionalFieldOf("mob-hat-chance", MOB_HAT_CHANCE),
					"The chance, in percent (0 to 100), that a zombie, husk, zombie villager, skeleton, stray, bogged, parched or creeper spawns wearing "
							+ "a Classic hat, at local difficulty 0. "
							+ "It falls in a straight line to none at local difficulty 6.75. /itemskins spawnchance sets it too."
			).forGetter(SkinsSettings::mobHatChance)
	).apply(instance, SkinsSettings::new));

	/** These settings with another {@code mob-hat-chance}. */
	public SkinsSettings withMobHatChance(double percent) {
		return new SkinsSettings(perksAffectPlayers, perksEnabled, percent);
	}

	/** The settings file of one server root; reloads only when asked. */
	static ConfigContainer<SkinsSettings> container(Path file) {
		return ConfigContainer.Builder.create(CODEC, () -> DEFAULTS).withPath(file).build(MetacraftSkins.MOD_ID);
	}
}
