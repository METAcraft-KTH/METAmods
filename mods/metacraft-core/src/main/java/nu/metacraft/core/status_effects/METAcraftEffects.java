package nu.metacraft.core.status_effects;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import nu.metacraft.core.METAcraftCore;

public class METAcraftEffects {

	public static final Holder<MobEffect> FIRE = register("fire", new ApplyFire());
	public static final Holder<MobEffect> FREEZE = register("freeze", new Freezing());

	public static void init() {

	}

	private static Holder<MobEffect> register(String id, MobEffect statusEffect) {
		return Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, METAcraftCore.getID(id), statusEffect);
	}

}
