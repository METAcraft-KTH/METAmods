package metacraft.ovvar;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import metacraft.ovvar.store.DesignStoreConfig;
import metacraft.ovvar.store.StashConfig;
import net.fabricmc.loader.api.FabricLoader;
import se.metacraft.config.container.ConfigContainer;

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
 * @param server	     what this server calls itself ({@link ServerConfig})
 * @param designs	    where the players' wardrobes live and what sewing does without it ({@link DesignStoreConfig})
 * @param stash		  this server's role, and the rules for taking patches out of the stash ({@link StashConfig})
 */
public record OvvarConfig(boolean sewingMinigame, int stitches, ServerConfig server, DesignStoreConfig designs, StashConfig stash) {
	public static final int MIN_STITCHES = 1, MAX_STITCHES = 16;


	public static final MapCodec<OvvarConfig> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			ConfigFields.required(Codec.BOOL.fieldOf("sewing_minigame"), "true: sewing a patch on a stand opens the stitching dialog and takes a few pulls. false: one", "click sews.").forGetter(OvvarConfig::sewingMinigame),
			ConfigFields.required(Codec.intRange(MIN_STITCHES, MAX_STITCHES).fieldOf("stitches"), "How many pulls a cell-sized patch takes in the minigame (1-16); bigger patches take", "proportionally more.").forGetter(OvvarConfig::stitches),
			ConfigFields.field(ServerConfig.CODEC.codec(), "server", ServerConfig.DEFAULT, "What this server calls itself. (ovvar no longer sets the MOTD from it: that is", "server.properties' motd.)").forGetter(OvvarConfig::server),
			ConfigFields.field(DesignStoreConfig.CODEC.codec(), "designs", DesignStoreConfig.DEFAULT, "Where every player's wardrobe (their sewn patches per chapter and their stash of unsewn patches)", "is kept. All servers should point at the same store.").forGetter(OvvarConfig::designs),
			ConfigFields.field(StashConfig.CODEC.codec(), "stash", StashConfig.DEFAULT, "What this server is, and the rules for patches here. Patches live in a player's stash (shared by", "all servers), on their ovve, or as items in the world.").forGetter(OvvarConfig::stash)
	).apply(instance, (minigame, stitches, server, designs, stash) -> new OvvarConfig(minigame, stitches, server, designs, stash)));

	private static final ConfigContainer<OvvarConfig> CONTAINER = ConfigContainer.Builder.create(
			CODEC, () -> new OvvarConfig(true, 6, ServerConfig.DEFAULT, DesignStoreConfig.DEFAULT, StashConfig.DEFAULT)
	).withPath(FabricLoader.getInstance().getConfigDir().resolve(Ovvar.MOD_ID + ".json")).build(Ovvar.MOD_ID);


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
		CONTAINER.modify(config);
	}
}
