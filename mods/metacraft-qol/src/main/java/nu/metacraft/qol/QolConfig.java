package nu.metacraft.qol;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.loader.api.FabricLoader;
import se.metacraft.config.util.CommentCodec;
import se.metacraft.config.container.ConfigContainer;
import nu.metacraft.qol.concrete_cauldron.ConcreteCauldronConfig;
import nu.metacraft.qol.silence_mobs.SilenceMobsConfig;
import nu.metacraft.qol.void_anchor.VoidAnchorConfig;

import java.nio.file.Path;
import java.util.Optional;

/**
 * {@code config/metacraft-qol.json5}: one section per feature, also editable in game through
 * metacraft-config-gui ({@code /meta-config-screen metacraft-qol}). A missing section takes its
 * defaults, so a file written before a feature existed still loads.
 */
public record QolConfig(VoidAnchorConfig voidAnchor, ConcreteCauldronConfig concreteCauldron, SilenceMobsConfig silenceMobs) {

	public static final QolConfig DEFAULT = new QolConfig(VoidAnchorConfig.DEFAULT, ConcreteCauldronConfig.DEFAULT, SilenceMobsConfig.DEFAULT);

	public static final MapCodec<QolConfig> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					CommentCodec.comment(
							section("void_anchor", VoidAnchorConfig.CODEC.codec(), VoidAnchorConfig.DEFAULT),
							"The void anchor: a respawn anchor that catches you when you fall into the End void."
					).forGetter(QolConfig::voidAnchor),
					CommentCodec.comment(
							section("concrete_cauldron", ConcreteCauldronConfig.CODEC.codec(), ConcreteCauldronConfig.DEFAULT),
							"The concrete cauldron: concrete powder thrown into a water cauldron turns into concrete,",
							"the whole dropped stack for one level of water."
					).forGetter(QolConfig::concreteCauldron),
					CommentCodec.comment(
							section("silence_mobs", SilenceMobsConfig.CODEC.codec(), SilenceMobsConfig.DEFAULT),
							"Silence mobs: use a name tag named \"silence me\" on a mob to silence it, \"unsilence me\" to undo it."
					).forGetter(QolConfig::silenceMobs)
			).apply(instance, QolConfig::new)
	);

	public static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve(Qol.MODID + ".json5");

	private static final ConfigContainer<QolConfig> CONTAINER = ConfigContainer.Builder.create(
			CODEC, () -> DEFAULT
	).withPath(PATH).build(Qol.MODID);

	/**
	 * A feature's section: read as its defaults when missing, but always written out, so admins
	 * see every setting. (optionalFieldOf with a default would leave a section at its defaults out.)
	 */
	private static <T> MapCodec<T> section(String name, Codec<T> codec, T defaults) {
		return codec.optionalFieldOf(name).xmap(present -> present.orElse(defaults), Optional::of);
	}

	public static QolConfig getInstance() {
		return CONTAINER.get();
	}

	/** Reads the file again the next time the config is asked for. */
	public static void reload() {
		CONTAINER.reload();
	}

}
