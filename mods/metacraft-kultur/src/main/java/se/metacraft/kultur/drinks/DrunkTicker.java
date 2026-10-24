package se.metacraft.kultur.drinks;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Brewery's drunkenness, once a tick for every player: the meter drains, and from each step on its
 * effect comes at its rate, counted on the player's tick count as Brewery counts it.
 *
 * <ul>
 *   <li>30: weakness, every 4 s</li>
 *   <li>40: slowness, every 4 s</li>
 *   <li>50: the stagger, every 0.2 s</li>
 *   <li>60: nausea for 32 s, every 16 s</li>
 *   <li>70: one point of alcohol poisoning every 4 s, never below half a heart</li>
 *   <li>80: darkness, every 16 s</li>
 * </ul>
 *
 * <p>Brewery hides these effects; here each shows its icon (not its particles), and lasts its rate
 * plus {@link #SLACK} so the icon never lapses between two refreshes. Inebriated shows the step.
 */
public final class DrunkTicker {
	static final int EVERY_4_S = 80;
	static final int EVERY_16_S = 320;
	static final int STAGGER_EVERY = 4;
	/** How often Inebriated's level and timer are put right. */
	public static final int REFRESH_EVERY = 20;
	/** How much longer than its rate a step's effect lasts. */
	static final int SLACK = 40;
	static final int NAUSEA_TICKS = 32 * 20;

	private DrunkTicker() {}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) tick(player);
		});
	}

	/** One tick of a player's drunkenness. Public for the game tests. */
	public static void tick(ServerPlayer player) {
		double alcohol = Drunkenness.get(player);
		if (alcohol <= 0) {
			if (player.hasEffect(KulturDrinks.INEBRIATED)) player.removeEffect(KulturDrinks.INEBRIATED);
			return;
		}
		if (!player.isAlive()) return;
		alcohol = Alcohol.afterTicks(alcohol, 1);
		Drunkenness.set(player, alcohol);

		int step = Alcohol.step(alcohol);
		int tick = player.tickCount;
		if (step >= 2 && tick % EVERY_4_S == 0) show(player, MobEffects.WEAKNESS, EVERY_4_S + SLACK);
		if (step >= 3 && tick % EVERY_4_S == 0) show(player, MobEffects.SLOWNESS, EVERY_4_S + SLACK);
		if (step >= 4 && tick % STAGGER_EVERY == 0) stagger(player, alcohol);
		if (step >= 5 && tick % EVERY_16_S == 0) show(player, MobEffects.NAUSEA, Math.max(NAUSEA_TICKS, EVERY_16_S + SLACK));
		if (step >= 6 && tick % EVERY_4_S == 0) poison(player);
		if (step >= 7 && tick % EVERY_16_S == 0) show(player, MobEffects.DARKNESS, EVERY_16_S + SLACK);

		if (alcohol <= 0 || tick % REFRESH_EVERY == 0) refreshInebriated(player);
	}

	/**
	 * Puts Inebriated right: level the step, timer the time left until sober, gone when sober. Run every
	 * {@link #REFRESH_EVERY} ticks and at once whenever a drink, bread or milk changes the meter.
	 */
	public static void refreshInebriated(ServerPlayer player) {
		double alcohol = Drunkenness.get(player);
		if (alcohol <= 0) {
			if (player.hasEffect(KulturDrinks.INEBRIATED)) player.removeEffect(KulturDrinks.INEBRIATED);
			return;
		}
		int amplifier = Alcohol.step(alcohol) - 1;
		int duration = Alcohol.ticksUntilSober(alcohol);
		MobEffectInstance current = player.getEffect(KulturDrinks.INEBRIATED);
		if (current != null && current.getAmplifier() == amplifier && Math.abs(current.getDuration() - duration) <= REFRESH_EVERY) return;
		// Forced: a lower level or a shorter timer than the current one is a plain addEffect's no-op.
		player.forceAddEffect(new MobEffectInstance(KulturDrinks.INEBRIATED, duration, amplifier, false, false, true), null);
	}

	private static void show(ServerPlayer player, Holder<MobEffect> effect, int duration) {
		player.addEffect(new MobEffectInstance(effect, duration, 0, false, false, true));
	}

	/**
	 * Brewery's little steps: a push in a random, mostly level direction. The server does not move a
	 * player, their client does, so the push goes to their client as an explosion's knockback (which
	 * adds to how they are moving, where a motion packet would replace it), as Brewery sends it. Not
	 * while riding, gliding, flying or asleep, where a push does nothing useful.
	 */
	static void stagger(ServerPlayer player, double alcohol) {
		if (player.isPassenger() || player.isFallFlying() || player.getAbilities().flying || player.isSpectator() || player.isSleeping()) return;
		RandomSource random = player.getRandom();
		Vec3 direction = new Vec3(random.nextDouble() - 0.5, random.nextDouble() / 2 - 0.49, random.nextDouble() - 0.5);
		double length = Alcohol.staggerLength(alcohol, random.nextDouble());
		if (length <= 0 || direction.lengthSqr() == 0) return;
		Vec3 push = direction.normalize().scale(length);
		ToClient.send(player, new ClientboundExplodePacket(player.position().add(0, -99999, 0), 0, 0, Optional.of(push),
				ParticleTypes.UNDERWATER, BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.EMPTY), WeightedList.of(), false));
	}

	/**
	 * One point of alcohol poisoning, never taking the player below one health (half a heart): the
	 * band it comes in is three drinks away and drains slowly. The damage type scales with difficulty,
	 * as Brewery's does, so on hard the point is made smaller to keep that promise.
	 */
	static void poison(ServerPlayer player) {
		float health = player.getHealth();
		if (health <= 1) return;
		ServerLevel level = player.level();
		float scale = level.getDifficulty() == Difficulty.HARD ? 1.5f : 1;
		float amount = Math.min(1, (health - 1) / scale);
		player.hurtServer(level, KulturDamageTypes.alcoholPoisoning(level), amount);
	}
}
