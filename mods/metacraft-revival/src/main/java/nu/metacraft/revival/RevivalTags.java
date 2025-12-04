package nu.metacraft.revival;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;

public class RevivalTags {

	public static class Damage {

		public static final TagKey<DamageType> BYPASSES_REVIVAL = create("bypasses_revival");

		private static TagKey<DamageType> create(String name) {
			return TagKey.create(Registries.DAMAGE_TYPE, METAcraftRevival.getID(name));
		}

	}

}
