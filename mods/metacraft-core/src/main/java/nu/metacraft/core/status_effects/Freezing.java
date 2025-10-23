package nu.metacraft.core.status_effects;

import eu.pb4.polymer.core.api.other.PolymerStatusEffect;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.server.world.ServerWorld;

public class Freezing extends StatusEffect implements PolymerStatusEffect {
	protected Freezing() {
		super(StatusEffectCategory.HARMFUL, 0x22e9ff);
	}

	@Override
	public boolean applyUpdateEffect(ServerWorld world, LivingEntity entity, int amplifier) {
		if (entity.canFreeze()) {
			entity.setInPowderSnow(true);
			entity.setFrozenTicks(Math.min(entity.getMinFreezeDamageTicks(), entity.getFrozenTicks() + 1));
			if (amplifier > 0 && entity.isFrozen() && entity.age % 40 != 0) {
				if (entity.age % Math.max(1, (25 >> amplifier)) == 0) {
					entity.damage(world, world.getDamageSources().freeze(), 1);
				}
			}
		}
		return true;
	}

	@Override
	public boolean canApplyUpdateEffect(int duration, int amplifier) {
		return true;
	}
}
