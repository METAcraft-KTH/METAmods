package se.metacraft.ovvar;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The {@code mob_ovvar} block of {@code config/ovvar.json}: mobs that spawn in an ovve or a frack and
 * drop its patches. Every key has a default.
 *
 * @param enabled        whether mobs that would spawn in leggings or a chestplate sometimes wear a patched
 *                       ovve or frack instead; mobs already wearing one keep it
 * @param chance         chance (0-1) that a mob's spawn leggings are an ovve, or its spawn chestplate a frack
 * @param patchDropChance chance (0-1) that each patch such a mob wore drops when a player kills it
 */
public record MobOvvarConfig(boolean enabled, float chance, float patchDropChance) {
	public static final MobOvvarConfig DEFAULT = new MobOvvarConfig(true, 0.05f, 0.5f);

	public static final MapCodec<MobOvvarConfig> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			ConfigFields.field(Codec.BOOL, "enabled", DEFAULT.enabled, "Mobs that would spawn in leggings or a chestplate sometimes wear a patched ovve or frack", "instead.").forGetter(MobOvvarConfig::enabled),
			ConfigFields.field(Codec.floatRange(0, 1), "chance", DEFAULT.chance, "Chance (0-1) that a mob's spawn leggings are an ovve, or its spawn chestplate a frack.").forGetter(MobOvvarConfig::chance),
			ConfigFields.field(Codec.floatRange(0, 1), "patch_drop_chance", DEFAULT.patchDropChance, "Chance (0-1) that each patch such a mob wore drops when a player kills it. The ovve or", "frack itself never drops.").forGetter(MobOvvarConfig::patchDropChance)
	).apply(instance, (enabled, chance, patchDropChance) -> new MobOvvarConfig(enabled, chance, patchDropChance)));
}
