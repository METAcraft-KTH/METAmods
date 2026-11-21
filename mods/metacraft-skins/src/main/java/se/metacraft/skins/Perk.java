package se.metacraft.skins;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * One Perk of a skin, as its {@code skin.json5} defines it (GLOSSARY: Perk): the {@link Trigger}
 * that sets it off, its effects, and, for an event Trigger, the chance it fires (0–1) and its
 * per-player cooldown in seconds. {@code tooltip} replaces the generated line ({@link PerkLines}).
 * Values are in the definition's units: seconds for times (an aura's or a sound's {@code interval}
 * is in ticks), health points for amounts, and {@code level} from 1 (so the amplifier is level − 1).
 * A Perk is never stored on an item: it's read from the live skin each time it applies.
 */
public record Perk(Trigger trigger, List<Effect> effects, double chance, double cooldownSeconds, Optional<String> tooltip) {

	/** When a Perk applies: a lasting state (held, worn) or a moment (hit, kill, block break, hurt). */
	public enum Trigger {
		/** In the main hand (never the offhand). */
		HELD("While held"),
		/** In an armour slot. */
		WORN("While worn"),
		/** A melee hit with the item in the main hand. */
		HIT("On hit"),
		/** A melee killing blow with it; player kills count. */
		KILL("On kill"),
		/** A block actually broken with it in survival or adventure. */
		BLOCK_BREAK("On block break"),
		/** Any damage to whoever wears it; the attacker, if any, is the target. */
		HURT("When hurt");

		private final String phrase;

		Trigger(String phrase) {
			this.phrase = phrase;
		}

		/** As written in {@code skin.json5}. */
		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		/** "While held", "On hit": how a Perk line starts. */
		public String phrase() {
			return phrase;
		}

		/** Held and worn Perks last while the item is in place; the rest fire on an event. */
		public boolean lasting() {
			return this == HELD || this == WORN;
		}

		public static Optional<Trigger> byId(String id) {
			return Arrays.stream(values()).filter(t -> t.id().equals(id)).findFirst();
		}

		/** The Triggers a Kind allows (ticket 05). */
		public static Set<Trigger> allowedFor(Kind kind) {
			return switch (kind) {
				case SWORD, MACE, SPEAR -> Set.of(HELD, HIT, KILL);
				case AXE, PICKAXE, SHOVEL, HOE -> Set.of(HELD, HIT, KILL, BLOCK_BREAK);
				case HELMET, CHESTPLATE, LEGGINGS, BOOTS, ELYTRA -> Set.of(WORN, HURT);
			};
		}
	}

	/**
	 * Whom an effect is for: the holder ({@code self}), or {@code other}: the victim on hit or
	 * kill, the attacker when hurt, the block's position on block break.
	 */
	public enum Target {
		SELF, OTHER;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/** One effect of a Perk. */
	public sealed interface Effect permits Potion, AttributeChange, Fire, Knockback, Heal, Particle, Sound {
		Target target();
	}

	/**
	 * A potion effect at {@code level} (1 is vanilla's amplifier 0). Lasting: kept up while held or
	 * worn, so {@code durationSeconds} is 0. Timed (event Triggers): its own duration, reset to full
	 * when it fires again.
	 */
	public record Potion(Holder<MobEffect> effect, int level, double durationSeconds, Target target) implements Effect {
		public int amplifier() {
			return level - 1;
		}
	}

	/** A transient attribute modifier while held or worn (lasting only); several add up. */
	public record AttributeChange(Holder<Attribute> attribute, double amount, AttributeModifier.Operation operation) implements Effect {
		@Override
		public Target target() {
			return Target.SELF;
		}
	}

	/** Sets the target (the victim on hit, the attacker when hurt) on fire. */
	public record Fire(double seconds) implements Effect {
		@Override
		public Target target() {
			return Target.OTHER;
		}
	}

	/** Knocks the target back ({@code strength}, as vanilla's knockback) and up ({@code lift}, blocks per tick). */
	public record Knockback(double strength, double lift) implements Effect {
		@Override
		public Target target() {
			return Target.OTHER;
		}
	}

	/** Heals the holder by {@code amount} health points. */
	public record Heal(double amount) implements Effect {
		@Override
		public Target target() {
			return Target.SELF;
		}
	}

	/**
	 * Particles ({@code count} of them, each given a random motion of up to about {@code speed}). Lasting: an
	 * aura every {@code intervalTicks}, around the holder's body or the top of their head ({@code place});
	 * with {@code whileMoving}, only while they walk. On an event: once ({@code intervalTicks} 0), at the
	 * target, victim or block (when hurt, at the wearer or the attacker).
	 */
	public record Particle(ParticleOptions particle, int count, int intervalTicks, Target target, double speed, Place place,
			boolean whileMoving) implements Effect {}

	/**
	 * Where a lasting particle aura plays. {@link #HEAD}'s particles are sent to everyone but the
	 * holder, so they never get in the holder's own view.
	 */
	public enum Place {
		BODY("body"), HEAD("head");

		private final String id;

		Place(String id) {
			this.id = id;
		}

		/** As written in {@code skin.json5}. */
		public String id() {
			return id;
		}
	}

	/** A sound. Lasting: now and then, every {@code intervalTicks}. On an event: once ({@code intervalTicks} 0). */
	public record Sound(Holder<SoundEvent> sound, float volume, float pitch, int intervalTicks, Target target) implements Effect {}
}
