package se.metacraft.ovvar.content.enchantment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import se.metacraft.ovvar.ConfigFields;

/**
 * The {@code enchantments} block of {@code config/ovvar.json}: an on/off and the tunables for each
 * of the six ovve enchantments ({@link OvveEnchantments}). Off means the enchantment does nothing
 * and nothing offers it any more; it stays registered, so items that have it keep it.
 */
public record EnchantmentsConfig(Toggle enderPockets, Patchwork patchworkProtection, Magpie magpie, Toggle keepsake,
		Keyring keyring, SpilledDrink spilledDrink) {

	/** An enchantment with nothing to tune. */
	public record Toggle(boolean enabled) {
		public static final Toggle ON = new Toggle(true);
		public static final MapCodec<Toggle> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
				ConfigFields.field(Codec.BOOL, "enabled", true, "false: the enchantment does nothing and is no longer offered.").forGetter(Toggle::enabled)
		).apply(instance, Toggle::new));
	}

	/**
	 * @param patchesForMax how many sewn patches give the full protection (1-64)
	 * @param maxProtection the protection at that many patches, in Protection points (0-20; 4 = Protection IV)
	 */
	public record Patchwork(boolean enabled, int patchesForMax, float maxProtection) {
		public static final Patchwork DEFAULT = new Patchwork(true, 16, 4.0f);
		public static final MapCodec<Patchwork> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
				ConfigFields.field(Codec.BOOL, "enabled", true, "false: the enchantment does nothing and is no longer offered.").forGetter(Patchwork::enabled),
				ConfigFields.field(Codec.intRange(1, 64), "patches_for_max", DEFAULT.patchesForMax, "How many sewn patches give the full protection (1-64); fewer give a linear share.").forGetter(Patchwork::patchesForMax),
				ConfigFields.field(Codec.floatRange(0, 20), "max_protection", DEFAULT.maxProtection, "The protection at that many patches, in Protection points (0-20; 4 is Protection IV).").forGetter(Patchwork::maxProtection)
		).apply(instance, Patchwork::new));
	}

	/**
	 * @param chestChancePerLevel    added to a chest's patch chance per Magpie level (0-1)
	 * @param mobOvvarChancePerLevel added to a mob ovve's patch drop chance per Magpie level (0-1)
	 */
	public record Magpie(boolean enabled, float chestChancePerLevel, float mobOvvarChancePerLevel) {
		public static final Magpie DEFAULT = new Magpie(true, 0.03f, 0.10f);
		public static final MapCodec<Magpie> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
				ConfigFields.field(Codec.BOOL, "enabled", true, "false: the enchantment does nothing and is no longer offered.").forGetter(Magpie::enabled),
				ConfigFields.field(Codec.floatRange(0, 1), "chest_chance_per_level", DEFAULT.chestChancePerLevel, "Added to a loot chest's chance of holding a patch, per Magpie level (0-1).").forGetter(Magpie::chestChancePerLevel),
				ConfigFields.field(Codec.floatRange(0, 1), "mob_ovvar_chance_per_level", DEFAULT.mobOvvarChancePerLevel, "Added to the chance a mob in an ovve drops a patch, per Magpie level (0-1, capped at 1).").forGetter(Magpie::mobOvvarChancePerLevel)
		).apply(instance, Magpie::new));
	}

	/** @param swapBackTicks how long the tool stays in hand with no block broken before it goes back (1-1200) */
	public record Keyring(boolean enabled, int swapBackTicks) {
		public static final Keyring DEFAULT = new Keyring(true, 20);
		public static final MapCodec<Keyring> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
				ConfigFields.field(Codec.BOOL, "enabled", true, "false: the enchantment does nothing and is no longer offered.").forGetter(Keyring::enabled),
				ConfigFields.field(Codec.intRange(1, 1200), "swap_back_ticks", DEFAULT.swapBackTicks, "Ticks the pocket tool stays in hand with nothing broken before it goes back (1-1200).").forGetter(Keyring::swapBackTicks)
		).apply(instance, Keyring::new));
	}

	/**
	 * @param gapTicks          ticks between the first dose ending (or an instant one landing) and the second (0-24000)
	 * @param durationScale     the second dose's duration as a share of the first's (0-1)
	 * @param instantScale      an instant effect's second dose strength (0-1)
	 * @param dripIntervalTicks one drip, on average, every this many ticks (20-72000)
	 */
	public record SpilledDrink(boolean enabled, int gapTicks, float durationScale, float instantScale, int dripIntervalTicks) {
		public static final SpilledDrink DEFAULT = new SpilledDrink(true, 100, 0.5f, 0.5f, 200);
		public static final MapCodec<SpilledDrink> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
				ConfigFields.field(Codec.BOOL, "enabled", true, "false: the curse does nothing and is no longer offered. (It is off anyway without Brewery.)").forGetter(SpilledDrink::enabled),
				ConfigFields.field(Codec.intRange(0, 24000), "gap_ticks", DEFAULT.gapTicks, "Ticks after the first dose ends (an instant effect: after drinking) that the second one lands (0-24000).").forGetter(SpilledDrink::gapTicks),
				ConfigFields.field(Codec.floatRange(0, 1), "duration_scale", DEFAULT.durationScale, "The second dose's duration as a share of the first's (0-1).").forGetter(SpilledDrink::durationScale),
				ConfigFields.field(Codec.floatRange(0, 1), "instant_scale", DEFAULT.instantScale, "How strong an instant effect's second dose is (0-1).").forGetter(SpilledDrink::instantScale),
				ConfigFields.field(Codec.intRange(20, 72000), "drip_interval_ticks", DEFAULT.dripIntervalTicks, "The cursed ovve drips, on average, once every this many ticks (20-72000).").forGetter(SpilledDrink::dripIntervalTicks)
		).apply(instance, SpilledDrink::new));
	}

	public static final EnchantmentsConfig DEFAULT = new EnchantmentsConfig(Toggle.ON, Patchwork.DEFAULT, Magpie.DEFAULT, Toggle.ON,
			Keyring.DEFAULT, SpilledDrink.DEFAULT);

	public static final MapCodec<EnchantmentsConfig> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			ConfigFields.field(Toggle.CODEC.codec(), "ender_pockets", DEFAULT.enderPockets, "Ender Pockets: the pockets are the wearer's ender chest. While it is off they are ordinary pockets, and turning it back on throws whatever they gathered meanwhile on the ground.").forGetter(EnchantmentsConfig::enderPockets),
			ConfigFields.field(Patchwork.CODEC.codec(), "patchwork_protection", DEFAULT.patchworkProtection, "Patchwork Protection: protection that grows with the patches sewn on.").forGetter(EnchantmentsConfig::patchworkProtection),
			ConfigFields.field(Magpie.CODEC.codec(), "magpie", DEFAULT.magpie, "Magpie (I-III): better odds of finding patches. The chest bonus is built when loot tables load: a change here needs /reload or a restart.").forGetter(EnchantmentsConfig::magpie),
			ConfigFields.field(Toggle.CODEC.codec(), "keepsake", DEFAULT.keepsake, "Keepsake: the ovve, pockets and all, stays with you when you die.").forGetter(EnchantmentsConfig::keepsake),
			ConfigFields.field(Keyring.CODEC.codec(), "keyring", DEFAULT.keyring, "Keyring: the right tool from the pockets comes to hand while mining.").forGetter(EnchantmentsConfig::keyring),
			ConfigFields.field(SpilledDrink.CODEC.codec(), "spilled_drink_curse", DEFAULT.spilledDrink, "Curse of the Spilled Drink: every drink comes back a little later, weaker. Needs Brewery.").forGetter(EnchantmentsConfig::spilledDrink)
	).apply(instance, EnchantmentsConfig::new));

	/** The same config with Keyring's block replaced (a test, a command). */
	public EnchantmentsConfig keyring(Keyring keyring) {
		return new EnchantmentsConfig(enderPockets, patchworkProtection, magpie, keepsake, keyring, spilledDrink);
	}
}
