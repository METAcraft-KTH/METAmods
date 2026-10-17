package nu.metacraft.qol.silence_mobs;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import se.metacraft.config.util.CommentCodec;
import nu.metacraft.qol.QolConfig;

/** The silence mobs section of {@code metacraft-qol.json}. */
public record SilenceMobsConfig(boolean enabled) {

	public static final SilenceMobsConfig DEFAULT = new SilenceMobsConfig(true);

	public static final MapCodec<SilenceMobsConfig> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					CommentCodec.comment(
							Codec.BOOL.fieldOf("enabled"),
							"Whether a name tag named \"silence me\" or \"unsilence me\" silences or unsilences a mob.",
							"Off, those are just names."
					).forGetter(SilenceMobsConfig::enabled)
			).apply(instance, SilenceMobsConfig::new)
	);

	public static SilenceMobsConfig getInstance() {
		return QolConfig.getInstance().silenceMobs();
	}

}
