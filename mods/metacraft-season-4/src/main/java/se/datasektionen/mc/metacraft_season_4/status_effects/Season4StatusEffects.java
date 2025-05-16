package se.datasektionen.mc.metacraft_season_4.status_effects;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import se.datasektionen.mc.metacraft_season_4.Season4;

public class Season4StatusEffects {

	public static final RegistryEntry<StatusEffect> SMALLIFY = register("smallify", new Smallify());
	public static final RegistryEntry<StatusEffect> HEALTH_REDUCTION = register("health_reduction", new HealthReduction());
	public static final RegistryEntry<StatusEffect> FIRE = register("fire", new ApplyFire());
	public static final RegistryEntry<StatusEffect> FREEZE = register("freeze", new Freezing());

	public static void init() {

	}

	private static RegistryEntry<StatusEffect> register(String id, StatusEffect statusEffect) {
		return Registry.registerReference(Registries.STATUS_EFFECT, Season4.getID(id), statusEffect);
	}

}
