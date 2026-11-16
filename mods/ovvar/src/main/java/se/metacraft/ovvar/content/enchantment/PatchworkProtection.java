package se.metacraft.ovvar.content.enchantment;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import se.metacraft.ovvar.OvvarConfig;
import se.metacraft.ovvar.content.Looks;
import se.metacraft.ovvar.content.SpotPlacements;
import se.metacraft.ovvar.content.item.OvveItem;

/**
 * Patchwork Protection: the more patches sewn on the garment, the more it protects, linearly up to
 * {@code patches_for_max}. Added to vanilla's protection sum ({@code EnchantmentHelperMixin}), so
 * {@code CombatRules} caps the total at 20 as usual.
 */
public final class PatchworkProtection {
	private PatchworkProtection() {}

	/** Protection points for {@code sewn} patches at {@code level} (0 when the level is 0). */
	public static float protection(int sewn, int level, EnchantmentsConfig.Patchwork cfg) {
		if (level <= 0 || sewn <= 0) return 0f;
		return cfg.maxProtection() * Math.min(1f, (float) sewn / cfg.patchesForMax());
	}

	public static int sewnCount(ItemStack garment) {
		return SpotPlacements.asPlacementList(Looks.sewn(garment)).size();
	}

	/** What the wearer's garment adds against this damage; 0 for what Protection ignores. */
	public static float protection(LivingEntity wearer, DamageSource source) {
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return 0f;
		int level = OvveEnchantments.level(wearer, OvveEnchantments.PATCHWORK_PROTECTION);
		if (level <= 0) return 0f;
		return protection(sewnCount(OvveItem.worn(wearer)), level, OvvarConfig.get().enchantments().patchworkProtection());
	}
}
