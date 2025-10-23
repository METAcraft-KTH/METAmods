package nu.metacraft.core.status_effects;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import nu.metacraft.core.METAcraftCore;

public class METAcraftEffects {

	public static final RegistryEntry<StatusEffect> FIRE = register("fire", new ApplyFire());
	public static final RegistryEntry<StatusEffect> FREEZE = register("freeze", new Freezing());

	public static void init() {

	}

	private static RegistryEntry<StatusEffect> register(String id, StatusEffect statusEffect) {
		return Registry.registerReference(Registries.STATUS_EFFECT, METAcraftCore.getID(id), statusEffect);
	}

}
