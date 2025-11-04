package nu.metacraft.core.status_effects;

import eu.pb4.polymer.core.api.other.PolymerStatusEffect;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.InstantenousMobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

public class ApplyFire extends InstantenousMobEffect implements PolymerStatusEffect {
	public ApplyFire() {
		super(MobEffectCategory.HARMFUL, 16750848);
	}

	private void appendFire(LivingEntity entity, int seconds) {
		int ticks = Math.max(0, entity.getRemainingFireTicks()) + seconds * 20;
		entity.igniteForTicks(ticks);
	}

	@Override
	public boolean applyEffectTick(ServerLevel world, LivingEntity entity, int amplifier) {
		if (!entity.fireImmune()) {
			appendFire(entity, amplifier+1);
			return true;
		}
		return false;
	}

	@Override
	public void applyInstantenousEffect(ServerLevel world, @Nullable Entity effectEntity, @Nullable Entity attacker, LivingEntity target, int amplifier, double proximity) {
		if (!target.fireImmune()) {
			appendFire(target, (int) Math.round(amplifier * proximity));
			if (effectEntity != null && attacker instanceof LivingEntity livingAttacker) {
				target.setLastHurtByMob(livingAttacker);
			}
		}
	}
}
