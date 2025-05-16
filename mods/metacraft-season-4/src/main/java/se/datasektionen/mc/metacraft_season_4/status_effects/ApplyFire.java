package se.datasektionen.mc.metacraft_season_4.status_effects;

import eu.pb4.polymer.core.api.other.PolymerStatusEffect;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.InstantStatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;

public class ApplyFire extends InstantStatusEffect implements PolymerStatusEffect {
	public ApplyFire() {
		super(StatusEffectCategory.HARMFUL, 16750848);
	}

	private void appendFire(LivingEntity entity, int seconds) {
		int ticks = Math.max(0, entity.getFireTicks()) + seconds * 20;
		entity.setOnFireForTicks(ticks);
	}

	@Override
	public boolean applyUpdateEffect(ServerWorld world, LivingEntity entity, int amplifier) {
		if (!entity.isFireImmune()) {
			appendFire(entity, amplifier+1);
			return true;
		}
		return false;
	}

	@Override
	public void applyInstantEffect(ServerWorld world, @Nullable Entity effectEntity, @Nullable Entity attacker, LivingEntity target, int amplifier, double proximity) {
		if (!target.isFireImmune()) {
			appendFire(target, (int) Math.round(amplifier * proximity));
			if (effectEntity != null && attacker instanceof LivingEntity livingAttacker) {
				target.setAttacker(livingAttacker);
			}
		}
	}
}
