package metacraft.ovvar;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import metacraft.ovvar.store.DesignStoreConfig;
import metacraft.ovvar.store.StashConfig;
import net.fabricmc.loader.api.FabricLoader;
import nu.metacraft.lib.config.container.ConfigContainer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * {@code config/ovvar.json}. Written with defaults when missing; a file that does not parse is an
 * error at startup rather than silently replaced. {@code /ovvar minigame} edits and saves it.
 *
 * @param sewingMinigame sew on a stand through the stitching dialog ({@link metacraft.ovvar.sewing.SewingGame})
 *					   instead of in one click
 * @param stitches	   how many stitches a cell-sized patch takes in the minigame; a longer outline
 *					   takes proportionally more ({@link metacraft.ovvar.sewing.Seam#stitchesFor})
 * @param server	     what this server calls itself, for the MOTD ({@link ServerConfig}, {@link Motd})
 * @param designs	    where the players' wardrobes live and what sewing does without it ({@link DesignStoreConfig})
 * @param stash		  this server's role, and the rules for taking patches out of the stash ({@link StashConfig})
 */
public record OvvarConfig(boolean sewingMinigame, int stitches, ServerConfig server, DesignStoreConfig designs, StashConfig stash) {
	public static final int MIN_STITCHES = 1, MAX_STITCHES = 16;

	/** Written into the file as {@code _help}, since JSON has no comments. */
	public static final Map<String, String> HELP = new LinkedHashMap<>();
	static {
		HELP.put("_about", "Ovvar: student overalls with sewn-on patches. This file is rewritten by the mod (/ovvar minigame), so keep notes in the _help blocks or elsewhere. Every key has a default; a missing key means the default.");
		HELP.put("sewing_minigame", "true: sewing a patch on a stand opens the stitching dialog and takes a few pulls. false: one click sews.");
		HELP.put("stitches", "How many pulls a cell-sized patch takes in the minigame (1-16); bigger patches take proportionally more.");
		HELP.put("server", "What this server calls itself, for the MOTD. See its _help.");
		HELP.put("designs", "The shared wardrobe store: where players' patches live. See its _help.");
		HELP.put("stash", "What this server is (survival or minigame) and the rules for patches here. See its _help.");
	}

	// A block record ({@code server}/{@code designs}/{@code stash}) equal to its own default is not
	// "equal to the field's default" by fluke here: optionalFieldOf(key, default) omits the field
	// (and so the block's own _help) whenever the value equals that default, which for a record with
	// its factory-fresh settings is the common case, not a fluke. optionalFieldOf(key) (no default)
	// always encodes the Optional it is given, so wrapping it with xmap keeps every block (and its
	// _help) in the file no matter what it is set to, while still defaulting to it when missing.
	public static final MapCodec<OvvarConfig> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("_help", Map.<String, String>of()).forGetter(c -> HELP),
			Codec.BOOL.fieldOf("sewing_minigame").forGetter(OvvarConfig::sewingMinigame),
			Codec.intRange(MIN_STITCHES, MAX_STITCHES).fieldOf("stitches").forGetter(OvvarConfig::stitches),
			ServerConfig.CODEC.codec().optionalFieldOf("server").xmap(o -> o.orElse(ServerConfig.DEFAULT), Optional::of).forGetter(OvvarConfig::server),
			DesignStoreConfig.CODEC.codec().optionalFieldOf("designs").xmap(o -> o.orElse(DesignStoreConfig.DEFAULT), Optional::of).forGetter(OvvarConfig::designs),
			StashConfig.CODEC.codec().optionalFieldOf("stash").xmap(o -> o.orElse(StashConfig.DEFAULT), Optional::of).forGetter(OvvarConfig::stash)
	).apply(instance, (help, minigame, stitches, server, designs, stash) -> new OvvarConfig(minigame, stitches, server, designs, stash)));

	private static final ConfigContainer<OvvarConfig> CONTAINER = ConfigContainer.Builder.create(
			CODEC, () -> new OvvarConfig(true, 6, ServerConfig.DEFAULT, DesignStoreConfig.DEFAULT, StashConfig.DEFAULT)
	).build(FabricLoader.getInstance().getConfigDir().resolve(Ovvar.MOD_ID + ".json"));


	public static OvvarConfig get() {
		return CONTAINER.get();
	}

	public OvvarConfig minigame(boolean on, int stitches) {
		return new OvvarConfig(on, stitches > 0 ? stitches : stitches(), server, designs, stash);
	}

	/** The same config with other store settings (a test, a command). */
	public OvvarConfig designs(DesignStoreConfig designs) {
		return new OvvarConfig(sewingMinigame, stitches, server, designs, stash);
	}

	/** Re-reads {@code config/ovvar.json}. */
	public static void reload() {
		CONTAINER.reload();
	}

	public static void modify(UnaryOperator<OvvarConfig> config) {
		CONTAINER.replace(config.apply(CONTAINER.get()));
	}
}
