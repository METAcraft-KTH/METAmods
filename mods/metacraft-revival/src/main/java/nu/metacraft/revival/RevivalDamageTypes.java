package nu.metacraft.revival;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;

public class RevivalDamageTypes {

	public static final ResourceKey<DamageType> ACCEPTED_FATE = create("accepted_fate");

	private static ResourceKey<DamageType> create(String id) {
		return ResourceKey.create(Registries.DAMAGE_TYPE, METAcraftRevival.getID(id));
	}

}
