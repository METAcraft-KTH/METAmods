package nu.metacraft.repair_fix.parse;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

import java.util.function.Predicate;
import java.util.function.Supplier;

public class EnchantmentParse extends ParseBase<RegistryEntry<Enchantment>> {

	private final int additionalCost;

	public EnchantmentParse(
			Predicate<RegistryEntry<Enchantment>> firstCheck,
			Predicate<RegistryEntry<Enchantment>> secondCheck,
			boolean combine, int additionalCost
	) {
		super(firstCheck, secondCheck, combine);
		this.additionalCost = additionalCost;
	}

	public int getAdditionalCost() {
		return additionalCost;
	}


	public static Predicate<RegistryEntry<Enchantment>> fromId(Identifier id) {
		return wrap(() -> fromId(RegistryKey.of(RegistryKeys.ENCHANTMENT, id)));
	}
	public static Predicate<RegistryEntry<Enchantment>> fromId(RegistryKey<Enchantment> id) {
		return enchantment -> {
			return id == enchantment.getKey().orElse(null);
		};
	}

	public static Predicate<RegistryEntry<Enchantment>> fromTag(TagKey<Enchantment> tag) {
		return enchantment -> enchantment.isIn(tag);
	}

	public static Predicate<RegistryEntry<Enchantment>> fromString(String key) {
		if (key.startsWith("#")) {
			return wrap(() -> fromTag(
				TagKey.of(RegistryKeys.ENCHANTMENT, Identifier.tryParse(key.substring(1)))
			));
		}
		return fromId(Identifier.tryParse(key));
	}

	/**
	 * Since this runs before bootstrap, this schedules initialisation code to when the predicate runs.
	 * Useful when using functions that require access to registries.
	 * @param wrap Supplier of the predicate to wrap.
	 * @return A predicate containing the wrapped predicate.
	 */
	private static Predicate<RegistryEntry<Enchantment>> wrap(Supplier<Predicate<RegistryEntry<Enchantment>>> wrap) {
		return enchantment -> wrap.get().test(enchantment);
	}

}
