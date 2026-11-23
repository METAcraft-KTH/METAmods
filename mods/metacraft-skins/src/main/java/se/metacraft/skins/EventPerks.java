package se.metacraft.skins;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fires event Perks (hit, kill, block break, hurt) when their event happens, read from the live
 * skin at that moment, for the item's Owner only ({@link Skins} checks). Each Perk rolls its
 * {@code chance} per event, and its {@code cooldown} is the player's: kept per player (and skin and
 * Perk), on the server's tick clock, so handing the item over and back never resets it. One per {@link Skins}, so a
 * test's own Skins never sees another's cooldowns.
 */
final class EventPerks {
	/** Per player: the server tick at which each of their cooled-down Perks ("skin/index") may fire again. */
	private final Map<UUID, Map<String, Long>> readyAt = new HashMap<>();

	/**
	 * What {@code target: "other"} means for one event: the victim or the attacker ({@code entity}),
	 * or the broken block ({@code entity} null, {@code at} its centre). Particles and sounds play at {@code at}.
	 */
	record Other(@Nullable LivingEntity entity, Vec3 at) {
		static Other of(LivingEntity entity) {
			return new Other(entity, new Vec3(entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ()));
		}
	}

	/**
	 * Fires the skin's Perks of this Trigger for the holder, each if it's off cooldown and its
	 * chance comes up. With no {@code other}, only effects on the holder; with
	 * {@code affectPlayers} false, none on another player either.
	 */
	void fire(ServerPlayer holder, LiveSkin skin, Perk.Trigger trigger, @Nullable Other other, boolean affectPlayers) {
		Other others = other != null && other.entity() instanceof Player && !affectPlayers ? null : other;
		long now = holder.level().getServer().getTickCount();
		List<Perk> perks = skin.perks();
		for (int i = 0; i < perks.size(); i++) {
			Perk perk = perks.get(i);
			if (perk.trigger() != trigger) continue;
			String key = skin.id() + "/" + i;
			Map<String, Long> cooling = readyAt.get(holder.getUUID());
			if (cooling != null && cooling.getOrDefault(key, Long.MIN_VALUE) > now) continue;
			if (perk.chance() < 1 && holder.getRandom().nextDouble() >= perk.chance()) continue;
			if (perk.cooldownSeconds() > 0) {
				Map<String, Long> mine = readyAt.computeIfAbsent(holder.getUUID(), u -> new HashMap<>());
				mine.values().removeIf(ready -> ready <= now);
				mine.put(key, now + ticks(perk.cooldownSeconds()));
			}
			for (Perk.Effect effect : perk.effects()) {
				Other target = effect.target() == Perk.Target.SELF ? Other.of(holder) : others;
				if (target != null) apply(holder, effect, target);
			}
		}
	}

	private static void apply(ServerPlayer holder, Perk.Effect effect, Other target) {
		ServerLevel level = holder.level();
		LivingEntity entity = target.entity();
		Vec3 at = target.at();
		switch (effect) {
			case Perk.Potion p -> {
				if (entity != null) entity.addEffect(new MobEffectInstance(p.effect(), ticks(p.durationSeconds()), p.amplifier()), holder);
			}
			case Perk.Heal h -> holder.heal((float) h.amount());
			case Perk.Fire f -> {
				if (entity != null) entity.igniteForTicks(ticks(f.seconds()));
			}
			case Perk.Knockback k -> {
				if (entity == null) return;
				// Away from the holder, as a vanilla knockback is; then up by the lift.
				entity.knockback(k.strength(), holder.getX() - entity.getX(), holder.getZ() - entity.getZ(), holder.damageSources().playerAttack(holder), 0);
				entity.push(0, k.lift(), 0);
				entity.needsSync = true;
			}
			case Perk.Particle p -> {
				double width = entity == null ? 0.5 : entity.getBbWidth() * 0.5, height = entity == null ? 0.5 : entity.getBbHeight() * 0.3;
				level.sendParticles(p.particle(), at.x, at.y, at.z, p.count(), width, height, width, p.speed());
			}
			case Perk.Sound s -> level.playSeededSound(null, at.x, at.y, at.z, s.sound(), SoundSource.PLAYERS, s.volume(), s.pitch(), holder.getRandom().nextLong());
			case Perk.AttributeChange a -> {}   // lasting only: never in an event Perk (the definition is rejected)
		}
	}

	/** Seconds as game ticks. */
	private static int ticks(double seconds) {
		return (int) Math.round(seconds * 20);
	}
}
