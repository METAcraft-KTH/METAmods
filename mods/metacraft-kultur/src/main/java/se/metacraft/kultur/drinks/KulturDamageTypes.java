package se.metacraft.kultur.drinks;

import se.metacraft.kultur.Kultur;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;

/**
 * Alcohol poisoning, a copy of Brewery's: {@code data/kultur/damage_type/alcohol_poisoning.json}
 * (loaded only without Brewery), its death messages in the lang file.
 */
public final class KulturDamageTypes {
	public static final ResourceKey<DamageType> ALCOHOL_POISONING =
			ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(Kultur.NAMESPACE, "alcohol_poisoning"));

	private KulturDamageTypes() {}

	public static DamageSource alcoholPoisoning(ServerLevel level) {
		return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(ALCOHOL_POISONING));
	}
}
