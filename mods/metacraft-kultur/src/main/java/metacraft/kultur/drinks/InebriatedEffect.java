package metacraft.kultur.drinks;

import eu.pb4.polymer.core.api.other.PolymerMobEffect;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffects;

/**
 * Inebriated: on a player for as long as there is alcohol in them, its level the step they are on
 * and its timer the time left until sober ({@link DrunkTicker}). It does nothing itself; each step's
 * effects are effects of their own. A vanilla client is told it is Luck, which Kultur's pack draws
 * with PolymITer's icon and names "Inebriated".
 */
public final class InebriatedEffect extends MobEffect implements PolymerMobEffect {
	/** PolymITer's colour for the drinks' effect. */
	private static final int COLOUR = 0x9E1515;

	InebriatedEffect() {
		super(MobEffectCategory.NEUTRAL, COLOUR);
	}

	@Override
	public MobEffect getPolymerReplacement(MobEffect effect, PacketContext context) {
		return MobEffects.LUCK.value();
	}
}
