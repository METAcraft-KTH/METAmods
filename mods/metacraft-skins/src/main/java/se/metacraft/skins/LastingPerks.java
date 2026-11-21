package se.metacraft.skins;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps each player's held and worn Perks in step with what they hold and wear, one call per
 * tick ({@link #update}); the Perks themselves come from the live skins every time, never from
 * the item. One per {@link Skins}, which only ever touches what it applied itself.
 * <ul>
 *   <li><b>Potions</b> are kept topped up between {@link #TOP_UP_BELOW} and {@link #TOP} ticks, so a
 *       client never shows them running out (vanilla blinks the icon under 200), and are taken off
 *       the tick the item goes. They're ambient, show their HUD icon and no swirl particles. The
 *       strongest wins: a stronger or equal effect from elsewhere is left alone, and a weaker one
 *       waits underneath (vanilla's hidden effect) and comes back, with its own time left, when
 *       the Perk's ends.</li>
 *   <li><b>Attributes</b> are transient modifiers, one per item and effect, so several add up.</li>
 *   <li><b>Auras and ambient sounds</b> play every {@code interval} ticks of the server's clock.</li>
 * </ul>
 */
final class LastingPerks {
	/** The duration a Perk's potion is given, and the one below which it's topped up again (ticks). */
	static final int TOP = 300, TOP_UP_BELOW = 240;

	/** A lasting Perk in place on a player: where its item is ("held", "worn/head", …), the Perk and its place in the skin's list. */
	record Placed(String slot, int index, Perk perk) {}

	/** Per player: each effect this keeps up, at which amplifier (it may be waiting under a stronger one). */
	private final Map<UUID, Map<Holder<MobEffect>, Integer>> potions = new HashMap<>();
	/** Per player: each modifier this added, on which attribute. */
	private final Map<UUID, Map<Identifier, Holder<Attribute>>> modifiers = new HashMap<>();
	/** Per player: where they were last tick, to tell whether they're moving. */
	private final Map<UUID, Vec3> lastPositions = new HashMap<>();
	/** Moving: more than this far sideways in a tick (blocks). Sneaking is about 0.065, walking 0.216. */
	static final double MOVING = 0.03;
	/** A head aura's centre, above the eyes (blocks), and how far its particles spread from it. */
	private static final double HEAD_TOP = 0.35, HEAD_SPREAD = 0.25;
	/** How far behind the eyes the player's own head aura is centred (blocks): at the back of the head. */
	static final double BEHIND = 0.45;

	void update(ServerPlayer player, List<Placed> lasting) {
		Map<Holder<MobEffect>, Integer> wantedPotions = new LinkedHashMap<>();
		Map<Identifier, Wanted> wantedModifiers = new LinkedHashMap<>();
		long tick = player.level().getServer().getTickCount();
		Vec3 now = player.position();
		Vec3 before = lastPositions.put(player.getUUID(), now);
		boolean moving = before != null && now.subtract(before).horizontalDistanceSqr() > MOVING * MOVING;
		for (Placed placed : lasting) {
			List<Perk.Effect> effects = placed.perk().effects();
			for (int i = 0; i < effects.size(); i++) {
				switch (effects.get(i)) {
					case Perk.Potion p -> wantedPotions.merge(p.effect(), p.amplifier(), Math::max);
					case Perk.AttributeChange a -> {
						Identifier id = Identifier.fromNamespaceAndPath(MetacraftSkins.MOD_ID, "perk/" + placed.slot() + "/" + placed.index() + "/" + i);
						wantedModifiers.put(id, new Wanted(a.attribute(), new AttributeModifier(id, a.amount(), a.operation())));
					}
					case Perk.Particle p -> {
						if (tick % p.intervalTicks() == 0 && (!p.whileMoving() || moving)) aura(player, p);
					}
					case Perk.Sound s -> {
						if (tick % s.intervalTicks() == 0) {
							player.level().playSeededSound(null, player.getX(), player.getY(), player.getZ(), s.sound(), SoundSource.PLAYERS, s.volume(), s.pitch(),
									player.getRandom().nextLong());
						}
					}
					default -> {}   // event effects: never in a lasting Perk (the definition is rejected)
				}
			}
		}
		potions(player, wantedPotions);
		attributes(player, wantedModifiers);
	}

	/** The player is leaving (or their Perks must all end): everything this keeps up on them is taken off. */
	void end(ServerPlayer player) {
		potions(player, Map.of());
		attributes(player, Map.of());
		potions.remove(player.getUUID());
		modifiers.remove(player.getUUID());
		lastPositions.remove(player.getUUID());
	}

	/**
	 * One burst of a particle aura: around the body, sent to everyone near; or around the top of the
	 * head. Everyone else near sees a head aura all round the head. The player sees their own just
	 * behind it ({@link #behindTheHead}), so it's never in their view in first person but shows in third.
	 */
	private static void aura(ServerPlayer player, Perk.Particle p) {
		ServerLevel level = player.level();
		if (p.place() == Perk.Place.BODY) {
			level.sendParticles(p.particle(), player.getX(), player.getY() + player.getBbHeight() * 0.6, player.getZ(), p.count(),
					player.getBbWidth() * 0.6, player.getBbHeight() * 0.3, player.getBbWidth() * 0.6, p.speed());
			return;
		}
		double y = player.getEyeY() + HEAD_TOP;
		for (ServerPlayer viewer : level.players()) {
			if (viewer == player) continue;
			level.sendParticles(viewer, p.particle(), false, false, player.getX(), y, player.getZ(), p.count(), HEAD_SPREAD, HEAD_SPREAD * 0.5, HEAD_SPREAD, p.speed());
		}
		behindTheHead(player, p);
	}

	/**
	 * The player's own head aura: each particle sent on its own, placed {@link #BEHIND} the head (by where
	 * they face) and spread no further forward than that, with a random motion of up to {@code speed} that
	 * never heads forward. So it stays behind the camera in first person.
	 */
	private static void behindTheHead(ServerPlayer player, Perk.Particle p) {
		RandomSource random = player.getRandom();
		Vec3 forward = Vec3.directionFromRotation(0, player.getYRot());
		Vec3 centre = player.getEyePosition().add(0, HEAD_TOP, 0).subtract(forward.scale(BEHIND));
		for (int i = 0; i < p.count(); i++) {
			Vec3 at = centre.add(notForward(new Vec3(spread(random, HEAD_SPREAD), spread(random, HEAD_SPREAD * 0.5), spread(random, HEAD_SPREAD)), forward));
			Vec3 motion = notForward(new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).scale(p.speed()), forward);
			// A count of 0 sends one particle with exactly this motion.
			player.level().sendParticles(player, p.particle(), false, false, at.x, at.y, at.z, 0, motion.x, motion.y, motion.z, 1);
		}
	}

	private static double spread(RandomSource random, double half) {
		return (random.nextDouble() * 2 - 1) * half;
	}

	/** {@code v} without any part along {@code forward} (a flat unit vector): it may point back or sideways, never ahead. */
	private static Vec3 notForward(Vec3 v, Vec3 forward) {
		double ahead = v.x * forward.x + v.z * forward.z;
		return ahead > 0 ? v.subtract(forward.scale(ahead)) : v;
	}

	private record Wanted(Holder<Attribute> attribute, AttributeModifier modifier) {}

	private void attributes(ServerPlayer player, Map<Identifier, Wanted> wanted) {
		Map<Identifier, Holder<Attribute>> added = modifiers.computeIfAbsent(player.getUUID(), u -> new HashMap<>());
		for (Iterator<Map.Entry<Identifier, Holder<Attribute>>> it = added.entrySet().iterator(); it.hasNext(); ) {
			var e = it.next();
			Wanted still = wanted.get(e.getKey());
			if (still != null && still.attribute().equals(e.getValue())) continue;
			AttributeInstance instance = player.getAttribute(e.getValue());
			if (instance != null) instance.removeModifier(e.getKey());
			it.remove();
		}
		for (var e : wanted.entrySet()) {
			AttributeInstance instance = player.getAttribute(e.getValue().attribute());
			if (instance == null) continue;
			if (!e.getValue().modifier().equals(instance.getModifier(e.getKey()))) instance.addOrUpdateTransientModifier(e.getValue().modifier());
			added.put(e.getKey(), e.getValue().attribute());
		}
	}

	private void potions(ServerPlayer player, Map<Holder<MobEffect>, Integer> wanted) {
		Map<Holder<MobEffect>, Integer> kept = potions.computeIfAbsent(player.getUUID(), u -> new HashMap<>());
		for (Iterator<Map.Entry<Holder<MobEffect>, Integer>> it = kept.entrySet().iterator(); it.hasNext(); ) {
			var e = it.next();
			if (e.getValue().equals(wanted.get(e.getKey()))) continue;
			takeOff(player, e.getKey(), e.getValue());
			it.remove();
		}
		for (var e : wanted.entrySet()) {
			keepUp(player, e.getKey(), e.getValue());
			kept.put(e.getKey(), e.getValue());
		}
	}

	private static MobEffectInstance ours(Holder<MobEffect> effect, int amplifier, @Nullable MobEffectInstance under) {
		return new MobEffectInstance(effect, TOP, amplifier, true, false, true, under);
	}

	/** Is this instance one of ours at that amplifier: ambient, no particles, an icon, and never longer than ours are? */
	private static boolean isOurs(MobEffectInstance instance, int amplifier) {
		return instance.isAmbient() && !instance.isVisible() && instance.showIcon() && !instance.isInfiniteDuration()
				&& instance.getDuration() <= TOP && instance.getAmplifier() == amplifier;
	}

	private static void keepUp(ServerPlayer player, Holder<MobEffect> effect, int amplifier) {
		MobEffectInstance current = player.getEffect(effect);
		if (current == null) {
			player.addEffect(ours(effect, amplifier, null));
		} else if (isOurs(current, amplifier)) {
			// Same amplifier, longer: vanilla only extends the duration, keeping whatever waits underneath.
			if (current.getDuration() < TOP_UP_BELOW) player.addEffect(ours(effect, amplifier, null));
		} else if (current.getAmplifier() < amplifier) {
			// A weaker effect from elsewhere waits underneath ours, with its own time left.
			if (current.isInfiniteDuration() || current.getDuration() > TOP) {
				player.addEffect(ours(effect, amplifier, null));   // vanilla keeps the longer, weaker one as hidden
			} else {
				MobEffectInstance under = new MobEffectInstance(current);
				player.removeEffect(effect);
				player.addEffect(ours(effect, amplifier, under));
			}
		}
		// A stronger or equal effect from elsewhere wins; ours comes back when it ends.
	}

	/** Takes ours off, wherever it is in the effect's chain, leaving every other instance (and its time) as it was. */
	private static void takeOff(ServerPlayer player, Holder<MobEffect> effect, int amplifier) {
		MobEffectInstance current = player.getEffect(effect);
		if (current == null) return;
		List<MobEffectInstance> chain = chain(current);
		List<MobEffectInstance> others = chain.stream().filter(i -> !isOurs(i, amplifier) && (i.isInfiniteDuration() || i.getDuration() > 0)).toList();
		if (others.size() == chain.size()) return;
		MobEffectInstance rebuilt = null;
		for (MobEffectInstance i : others.reversed()) {
			rebuilt = new MobEffectInstance(i.getEffect(), i.getDuration(), i.getAmplifier(), i.isAmbient(), i.isVisible(), i.showIcon(), rebuilt);
		}
		player.removeEffect(effect);
		if (rebuilt != null) player.addEffect(rebuilt);
	}

	/** The instance and those hidden under it, top first (vanilla has no accessor, so read through its codec). */
	private static List<MobEffectInstance> chain(MobEffectInstance top) {
		List<MobEffectInstance> chain = new ArrayList<>();
		JsonObject json = MobEffectInstance.CODEC.encodeStart(JsonOps.INSTANCE, top).getOrThrow().getAsJsonObject();
		while (json != null) {
			JsonObject hidden = json.has("hidden_effect") ? json.getAsJsonObject("hidden_effect").deepCopy() : null;
			json.remove("hidden_effect");
			if (!json.has("id")) json.addProperty("id", top.getEffect().unwrapKey().orElseThrow().identifier().toString());
			chain.add(MobEffectInstance.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
			json = hidden;
		}
		return chain;
	}
}
