package se.metacraft.ovvar;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import se.metacraft.ovvar.content.enchantment.EnchantmentsConfig;
import se.metacraft.ovvar.store.DesignStoreConfig;
import se.metacraft.ovvar.store.StashConfig;
import se.metacraft.config.container.ConfigContainer;
import se.metacraft.ovvar.sewing.Seam;
import se.metacraft.ovvar.sewing.SewingGame;

import java.util.function.UnaryOperator;

/**
 * {@code config/ovvar.json}. Written with defaults when missing; a file that does not parse is an
 * error at startup rather than silently replaced. {@code /ovvar minigame} edits and saves it.
 *
 * @param sewingMinigame sew on a stand through the stitching dialog ({@link SewingGame})
 *					   instead of in one click
 * @param stitches	   how many stitches a cell-sized patch takes in the minigame; a longer outline
 *					   takes proportionally more ({@link Seam#stitchesFor})
 * @param designs	    where the players' wardrobes live and what sewing does without it ({@link DesignStoreConfig})
 * @param stash		  this server's role, and the rules for taking patches out of the stash ({@link StashConfig})
 * @param shaders	    where players patch their shaderpack ({@link ShadersConfig})
 * @param mobOvvar	   mobs that spawn in an ovve or a frack and drop its patches ({@link MobOvvarConfig})
 * @param punkphoz	   the wandering trader Punkphöz ({@link PunkphozConfig})
 * @param enchantments   the six ovve enchantments' switches and tunables ({@link EnchantmentsConfig})
 */
public record OvvarConfig(boolean sewingMinigame, int stitches, DesignStoreConfig designs, StashConfig stash, ShadersConfig shaders, MobOvvarConfig mobOvvar, PunkphozConfig punkphoz, EnchantmentsConfig enchantments) {
	public static final int MIN_STITCHES = 1, MAX_STITCHES = 16;


	public static final MapCodec<OvvarConfig> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			ConfigFields.required(Codec.BOOL.fieldOf("sewing_minigame"), "true: sewing a patch on a stand opens the stitching dialog and takes a few pulls. false: one", "click sews.").forGetter(OvvarConfig::sewingMinigame),
			ConfigFields.required(Codec.intRange(MIN_STITCHES, MAX_STITCHES).fieldOf("stitches"), "How many pulls a cell-sized patch takes in the minigame (1-16); bigger patches take", "proportionally more.").forGetter(OvvarConfig::stitches),
			ConfigFields.field(DesignStoreConfig.CODEC.codec(), "designs", DesignStoreConfig.DEFAULT, "Where every player's wardrobe (their sewn patches per chapter and their stash of unsewn patches)", "is kept. All servers should point at the same store.").forGetter(OvvarConfig::designs),
			ConfigFields.field(StashConfig.CODEC.codec(), "stash", StashConfig.DEFAULT, "What this server is, and the rules for patches here. Patches live in a player's stash (shared by", "all servers), on their ovve, or as items in the world.").forGetter(OvvarConfig::stash),
			ConfigFields.field(ShadersConfig.CODEC.codec(), "shaders", ShadersConfig.DEFAULT, "Players with a shaderpack: where they patch it so ovvar draw right.").forGetter(OvvarConfig::shaders),
			ConfigFields.field(MobOvvarConfig.CODEC.codec(), "mob_ovvar", MobOvvarConfig.DEFAULT, "Mob ovvar: mobs that spawn in an ovve or a frack and drop its patches.").forGetter(OvvarConfig::mobOvvar),
			ConfigFields.field(PunkphozConfig.CODEC.codec(), "punkphoz", PunkphozConfig.DEFAULT, "Punkphoz: a wandering trader with his own stock, on top of the vanilla wanderers.").forGetter(OvvarConfig::punkphoz),
			ConfigFields.field(EnchantmentsConfig.CODEC.codec(), "enchantments", EnchantmentsConfig.DEFAULT, "The six enchantments only an ovve takes: each one's on/off and numbers. Off: it does nothing", "and is no longer offered, but stays on the items that have it.").forGetter(OvvarConfig::enchantments)
	).apply(instance, OvvarConfig::new));

	private static final ConfigContainer<OvvarConfig> CONTAINER = ConfigContainer.Builder.create(
			CODEC, () -> new OvvarConfig(true, 6, DesignStoreConfig.DEFAULT, StashConfig.DEFAULT, ShadersConfig.DEFAULT, MobOvvarConfig.DEFAULT, PunkphozConfig.DEFAULT, EnchantmentsConfig.DEFAULT)
	).build(Ovvar.MOD_ID);


	public static OvvarConfig get() {
		return CONTAINER.get();
	}

	public OvvarConfig minigame(boolean on, int stitches) {
		return new OvvarConfig(on, stitches > 0 ? stitches : stitches(), designs, stash, shaders, mobOvvar, punkphoz, enchantments);
	}

	/** The same config with other store settings (a test, a command). */
	public OvvarConfig designs(DesignStoreConfig designs) {
		return new OvvarConfig(sewingMinigame, stitches, designs, stash, shaders, mobOvvar, punkphoz, enchantments);
	}

	/** The same config with other shaderpack settings (a test). */
	public OvvarConfig shaders(ShadersConfig shaders) {
		return new OvvarConfig(sewingMinigame, stitches, designs, stash, shaders, mobOvvar, punkphoz, enchantments);
	}

	/** The same config with other mob ovvar settings (a test, a command). */
	public OvvarConfig mobOvvar(MobOvvarConfig mobOvvar) {
		return new OvvarConfig(sewingMinigame, stitches, designs, stash, shaders, mobOvvar, punkphoz, enchantments);
	}

	/** The same config with other enchantment settings (a test, a command). */
	public OvvarConfig enchantments(EnchantmentsConfig enchantments) {
		return new OvvarConfig(sewingMinigame, stitches, designs, stash, shaders, mobOvvar, punkphoz, enchantments);
	}

	/** Re-reads {@code config/ovvar.json}. */
	public static void reload() {
		CONTAINER.reload();
	}

	public static void modify(UnaryOperator<OvvarConfig> config) {
		CONTAINER.modify(config);
	}
}
