package nu.metacraft.core.status_effects;

import eu.pb4.polymer.core.api.other.PolymerMobEffect;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

public class Freezing extends MobEffect implements PolymerMobEffect {
	protected Freezing() {
		super(MobEffectCategory.HARMFUL, 0x22e9ff);
	}

	@Override
	public boolean applyEffectTick(ServerLevel world, LivingEntity entity, int amplifier) {
		if (entity.canFreeze()) {
			entity.setIsInPowderSnow(true);
			entity.setTicksFrozen(Math.min(entity.getTicksRequiredToFreeze(), entity.getTicksFrozen() + 1));
			if (amplifier > 0 && entity.isFullyFrozen() && entity.tickCount % 40 != 0) {
				if (entity.tickCount % Math.max(1, (25 >> amplifier)) == 0) {
					entity.hurtServer(world, world.damageSources().freeze(), 1);
				}
			}
		}
		return true;
	}

	@Override
	public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
		return true;
	}
}
