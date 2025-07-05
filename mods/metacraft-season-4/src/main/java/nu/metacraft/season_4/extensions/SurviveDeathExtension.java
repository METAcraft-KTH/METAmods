package nu.metacraft.season_4.extensions;

import net.minecraft.entity.damage.DamageSource;

public interface SurviveDeathExtension {

	default boolean metacraft_season_4$surviveDeath(DamageSource source) {
		return false;
	}

}
