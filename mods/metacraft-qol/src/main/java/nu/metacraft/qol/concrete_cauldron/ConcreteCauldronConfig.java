package nu.metacraft.qol.concrete_cauldron;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import se.metacraft.config.util.CommentCodec;
import nu.metacraft.qol.QolConfig;

/** The concrete cauldron's section of {@code metacraft-qol.json}. */
public record ConcreteCauldronConfig(boolean enabled) {

	public static final ConcreteCauldronConfig DEFAULT = new ConcreteCauldronConfig(true);

	public static final MapCodec<ConcreteCauldronConfig> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					CommentCodec.comment(
							Codec.BOOL.fieldOf("enabled"),
							"Whether concrete powder thrown into a water cauldron turns into concrete."
					).forGetter(ConcreteCauldronConfig::enabled)
			).apply(instance, ConcreteCauldronConfig::new)
	);

	public static ConcreteCauldronConfig getInstance() {
		return QolConfig.getInstance().concreteCauldron();
	}

}
