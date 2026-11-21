package se.metacraft.skins;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static se.metacraft.skins.DefinitionCodecs.NUMBER;
import static se.metacraft.skins.DefinitionCodecs.TEXT;
import static se.metacraft.skins.DefinitionCodecs.WHOLE;
import static se.metacraft.skins.DefinitionCodecs.at;
import static se.metacraft.skins.DefinitionCodecs.dispatch;
import static se.metacraft.skins.DefinitionCodecs.field;
import static se.metacraft.skins.DefinitionCodecs.list;
import static se.metacraft.skins.DefinitionCodecs.mapError;
import static se.metacraft.skins.DefinitionCodecs.object;
import static se.metacraft.skins.DefinitionCodecs.optionalField;
import static se.metacraft.skins.DefinitionCodecs.orElse;
import static se.metacraft.skins.DefinitionCodecs.rawField;

/**
 * The codecs of a skin definition's {@code perks} (ticket 05's catalogue), which check as they
 * read: each Perk's Trigger must be one its Kind allows, and each effect one its Trigger allows,
 * with only the fields that effect takes. Which codec reads a Perk depends on its Kind, and which
 * reads an effect on its Perk's Trigger and its own {@code type}, read first. Anything wrong
 * rejects the whole skin, saying which Perk and why.
 */
final class PerkCodecs {
	private PerkCodecs() {}

	/** A lasting aura's and ambient sound's default interval, in ticks. */
	static final int AURA_INTERVAL = 20, SOUND_INTERVAL = 100;
	private static final List<String> EFFECT_TYPES = List.of("attribute", "fire", "heal", "knockback", "particle", "potion", "sound");
	private static final String NO_EFFECTS = "\"effects\" must be a list of at least one effect";
	private static final String NOT_PARTICLE = "\"particle\" must be particle options, as in a data pack: { type: \"minecraft:...\" }";

	private static final Codec<Double> SECONDS = NUMBER.validate(d -> d > 0 ? DataResult.success(d) : DataResult.error(() -> "must be more than 0 seconds"));
	private static final Codec<Long> INTERVAL = WHOLE.validate(t -> t >= 1 ? DataResult.success(t) : DataResult.error(() -> "must be 1 tick or more"));
	private static final Codec<Perk.Target> TARGET = TEXT.comapFlatMap(id -> Arrays.stream(Perk.Target.values()).filter(t -> t.id().equals(id)).findFirst()
			.map(DataResult::success).orElseGet(() -> DataResult.error(() -> "is \"self\" or \"other\", not \"" + id + "\"")), Perk.Target::id);
	private static final Codec<AttributeModifier.Operation> OPERATION = TEXT.comapFlatMap(op -> Arrays.stream(AttributeModifier.Operation.values())
			.filter(o -> o.getSerializedName().equals(op)).findFirst().map(DataResult::success)
			.orElseGet(() -> DataResult.error(() -> "unknown operation \"" + op + "\" (one of: add_value, add_multiplied_base, add_multiplied_total)")),
			AttributeModifier.Operation::getSerializedName);
	/** Particle options as in a data pack. */
	private static final Codec<ParticleOptions> PARTICLE = object(mapError(ParticleTypes.CODEC, e -> at("particle", e)), NOT_PARTICLE);
	/** A sound needn't be vanilla's: a resource pack may add it. */
	private static final Codec<Holder<SoundEvent>> SOUND = TEXT.comapFlatMap(id -> {
		Identifier sound = Identifier.tryParse(id);
		if (sound == null) return DataResult.error(() -> "\"" + id + "\" is not an id");
		return DataResult.success(BuiltInRegistries.SOUND_EVENT.get(sound).<Holder<SoundEvent>>map(h -> h)
				.orElseGet(() -> Holder.direct(SoundEvent.createVariableRangeEvent(sound))));
	}, holder -> holder.value().location().toString());

	/** A skin's {@code perks}, for its Kind. */
	static Codec<List<Perk>> perks(Kind kind) {
		return list("perks", perk(kind), "\"perks\" must be a list of Perks");
	}

	private static Codec<Perk> perk(Kind kind) {
		Set<Perk.Trigger> allowed = Perk.Trigger.allowedFor(kind);
		Codec<Perk.Trigger> trigger = TEXT.comapFlatMap(id -> Perk.Trigger.byId(id).map(DataResult::success)
				.orElseGet(() -> DataResult.error(() -> "unknown trigger \"" + id + "\" (one of: " + ids(Arrays.stream(Perk.Trigger.values()).toList()) + ")")),
				Perk.Trigger::id).validate(t -> allowed.contains(t) ? DataResult.success(t) : DataResult.error(() -> "a " + kind.id() + " skin's Perks can't be \""
				+ t.id() + "\" (allowed: " + ids(Arrays.stream(Perk.Trigger.values()).filter(allowed::contains).toList()) + ")"));
		return object(dispatch(field("trigger", trigger), Perk::trigger, t -> StrictCodec.map(perkFields(t), "trigger")).codec(),
				"a Perk must be an object { trigger, effects }");
	}

	private static String ids(List<Perk.Trigger> triggers) {
		return triggers.stream().map(Perk.Trigger::id).collect(Collectors.joining(", "));
	}

	/** A Perk as written: what's left out is the Trigger's to fill in. */
	private record PerkSpec(List<Perk.Effect> effects, Optional<Double> chance, Optional<Double> cooldown, Optional<String> tooltip) {}

	private static MapCodec<Perk> perkFields(Perk.Trigger trigger) {
		Codec<List<Perk.Effect>> effects = list("effects", effect(trigger), NO_EFFECTS)
				.validate(e -> e.isEmpty() ? DataResult.error(() -> NO_EFFECTS) : DataResult.success(e));
		return RecordCodecBuilder.<PerkSpec>mapCodec(i -> i.group(
				rawField("effects", effects, NO_EFFECTS).forGetter(PerkSpec::effects),
				optionalField("chance", NUMBER.validate(c -> c >= 0 && c <= 1 ? DataResult.success(c) : DataResult.error(() -> "must be between 0 and 1")))
						.forGetter(PerkSpec::chance),
				optionalField("cooldown", NUMBER.validate(c -> c >= 0 ? DataResult.success(c) : DataResult.error(() -> "must be 0 or more seconds")))
						.forGetter(PerkSpec::cooldown),
				optionalField("tooltip", TEXT).forGetter(PerkSpec::tooltip)
		).apply(i, PerkSpec::new)).flatXmap(spec -> trigger.lasting() && (spec.chance().isPresent() || spec.cooldown().isPresent())
				? DataResult.error(() -> "chance and cooldown are for event Perks (hit, kill, block_break, hurt), not \"" + trigger.id() + "\"")
				: DataResult.success(new Perk(trigger, spec.effects(), spec.chance().orElse(1.0), spec.cooldown().orElse(0.0), spec.tooltip())),
				perk -> DataResult.success(new PerkSpec(perk.effects(), Optional.of(perk.chance()), Optional.of(perk.cooldownSeconds()), perk.tooltip())));
	}

	private static Codec<Perk.Effect> effect(Perk.Trigger trigger) {
		Set<String> allowed = allowedEffects(trigger);
		Codec<String> type = TEXT.validate(t -> !EFFECT_TYPES.contains(t)
				? DataResult.error(() -> "unknown effect type \"" + t + "\" (one of: " + String.join(", ", EFFECT_TYPES) + ")")
				: !allowed.contains(t)
				? DataResult.error(() -> "a \"" + trigger.id() + "\" Perk can't have a " + t + " effect (allowed: " + String.join(", ", allowed.stream().sorted().toList()) + ")")
				: DataResult.success(t));
		return object(DefinitionCodecs.<String, Perk.Effect>dispatch(field("type", type), PerkCodecs::type, t -> StrictCodec.map(effectFields(trigger, t), "type")).codec(),
				"an effect must be an object { type, ... }");
	}

	private static String type(Perk.Effect effect) {
		return switch (effect) {
			case Perk.Potion _ -> "potion";
			case Perk.AttributeChange _ -> "attribute";
			case Perk.Fire _ -> "fire";
			case Perk.Knockback _ -> "knockback";
			case Perk.Heal _ -> "heal";
			case Perk.Particle _ -> "particle";
			case Perk.Sound _ -> "sound";
		};
	}

	private static MapCodec<? extends Perk.Effect> effectFields(Perk.Trigger trigger, String type) {
		return switch (type) {
			case "potion" -> potion(trigger);
			case "attribute" -> attribute();
			case "fire" -> fire(trigger);
			case "knockback" -> knockback(trigger);
			case "heal" -> heal(trigger);
			case "particle" -> particle(trigger);
			case "sound" -> sound(trigger);
			default -> throw new IllegalStateException(type);
		};
	}

	private record PotionSpec(Holder<MobEffect> effect, Optional<Long> level, Optional<Double> duration, Perk.Target target) {}

	/** Lasting: kept up while the item is in place, so no duration. Timed: needs one. */
	private static MapCodec<Perk.Potion> potion(Perk.Trigger trigger) {
		return RecordCodecBuilder.<PotionSpec>mapCodec(i -> i.group(
				field("effect", registered(BuiltInRegistries.MOB_EFFECT, "effect")).forGetter(PotionSpec::effect),
				optionalField("level", WHOLE.validate(l -> l >= 1 ? DataResult.success(l) : DataResult.error(() -> "starts at 1"))).forGetter(PotionSpec::level),
				optionalField("duration", SECONDS).forGetter(PotionSpec::duration),
				target(trigger, "potion", potionTargets(trigger)).forGetter(PotionSpec::target)
		).apply(i, PotionSpec::new)).<Perk.Potion>flatXmap(spec -> {
			int level = (int) Math.min(spec.level().orElse(1L), Integer.MAX_VALUE);
			if (trigger.lasting()) {
				return spec.duration().isPresent()
						? DataResult.error(() -> "a \"" + trigger.id() + "\" potion lasts while the item is in place, so it takes no duration")
						: DataResult.success(new Perk.Potion(spec.effect(), level, 0, spec.target()));
			}
			return spec.duration().map(d -> DataResult.success(new Perk.Potion(spec.effect(), level, d, spec.target())))
					.orElseGet(() -> DataResult.error(() -> "a timed potion needs a duration (seconds)"));
		}, potion -> DataResult.success(new PotionSpec(potion.effect(), Optional.of((long) potion.level()),
				trigger.lasting() ? Optional.empty() : Optional.of(potion.durationSeconds()), potion.target())));
	}

	private static MapCodec<Perk.AttributeChange> attribute() {
		return RecordCodecBuilder.mapCodec(i -> i.group(
				field("attribute", registered(BuiltInRegistries.ATTRIBUTE, "attribute")).forGetter(Perk.AttributeChange::attribute),
				field("amount", NUMBER).forGetter(Perk.AttributeChange::amount),
				orElse(optionalField("operation", OPERATION), AttributeModifier.Operation.ADD_VALUE).forGetter(Perk.AttributeChange::operation)
		).apply(i, Perk.AttributeChange::new));
	}

	private static MapCodec<Perk.Fire> fire(Perk.Trigger trigger) {
		return RecordCodecBuilder.mapCodec(i -> i.group(
				target(trigger, "fire", Set.of(Perk.Target.OTHER)).forGetter(Perk.Fire::target),
				field("duration", SECONDS, "fire needs a duration (seconds)").forGetter(Perk.Fire::seconds)
		).apply(i, (target, seconds) -> new Perk.Fire(seconds)));
	}

	private static MapCodec<Perk.Knockback> knockback(Perk.Trigger trigger) {
		return RecordCodecBuilder.mapCodec(i -> i.group(
				target(trigger, "knockback", Set.of(Perk.Target.OTHER)).forGetter(Perk.Knockback::target),
				orElse(optionalField("strength", NUMBER.validate(s -> s >= 0 ? DataResult.success(s) : DataResult.error(() -> "must be 0 or more"))), 0.5)
						.forGetter(Perk.Knockback::strength),
				orElse(optionalField("lift", NUMBER), 0.0).forGetter(Perk.Knockback::lift)
		).apply(i, (target, strength, lift) -> new Perk.Knockback(strength, lift)));
	}

	private static MapCodec<Perk.Heal> heal(Perk.Trigger trigger) {
		return RecordCodecBuilder.mapCodec(i -> i.group(
				target(trigger, "heal", Set.of(Perk.Target.SELF)).forGetter(Perk.Heal::target),
				field("amount", NUMBER.validate(a -> a > 0 ? DataResult.success(a) : DataResult.error(() -> "must be more than 0")),
						"missing field \"amount\" (health points)").forGetter(Perk.Heal::amount)
		).apply(i, (target, amount) -> new Perk.Heal(amount)));
	}

	private record ParticleSpec(ParticleOptions particle, Optional<Long> count, Optional<Long> interval, Perk.Target target, Optional<Double> speed,
			Optional<Perk.Place> at, Optional<Boolean> whileMoving) {}

	private static final String PLACES = Arrays.stream(Perk.Place.values()).map(Perk.Place::id).collect(Collectors.joining(" or "));
	private static final Codec<Perk.Place> PLACE = mapError(Codec.STRING, e -> "must be " + PLACES).comapFlatMap(
			id -> Arrays.stream(Perk.Place.values()).filter(p -> p.id().equals(id)).findFirst().map(DataResult::success)
					.orElseGet(() -> DataResult.error(() -> "must be " + PLACES + ", not \"" + id + "\"")),
			Perk.Place::id);

	private static MapCodec<Perk.Particle> particle(Perk.Trigger trigger) {
		return RecordCodecBuilder.<ParticleSpec>mapCodec(i -> i.group(
				rawField("particle", PARTICLE, NOT_PARTICLE).forGetter(ParticleSpec::particle),
				optionalField("count", WHOLE.validate(c -> c >= 1 ? DataResult.success(c) : DataResult.error(() -> "must be 1 or more"))).forGetter(ParticleSpec::count),
				optionalField("interval", INTERVAL).forGetter(ParticleSpec::interval),
				target(trigger, "particle", sideTargets(trigger)).forGetter(ParticleSpec::target),
				optionalField("speed", NUMBER.validate(v -> v >= 0 ? DataResult.success(v) : DataResult.error(() -> "must be 0 or more"))).forGetter(ParticleSpec::speed),
				optionalField("at", PLACE).forGetter(ParticleSpec::at),
				optionalField("while_moving", mapError(Codec.BOOL, e -> "must be true or false")).forGetter(ParticleSpec::whileMoving)
		).apply(i, ParticleSpec::new)).flatXmap(
				spec -> {
					if (!trigger.lasting() && (spec.at().isPresent() || spec.whileMoving().isPresent())) {
						return DataResult.error(() -> "at and while_moving are for held and worn Perks only");
					}
					return interval(trigger, spec.interval(), AURA_INTERVAL).map(interval ->
							new Perk.Particle(spec.particle(), (int) Math.min(spec.count().orElse(1L), Integer.MAX_VALUE), interval, spec.target(),
									spec.speed().orElse(0.0), spec.at().orElse(Perk.Place.BODY), spec.whileMoving().orElse(false)));
				},
				particle -> DataResult.success(new ParticleSpec(particle.particle(), Optional.of((long) particle.count()),
						trigger.lasting() ? Optional.of((long) particle.intervalTicks()) : Optional.empty(), particle.target(),
						particle.speed() == 0 ? Optional.empty() : Optional.of(particle.speed()),
						trigger.lasting() ? Optional.of(particle.place()) : Optional.empty(),
						trigger.lasting() ? Optional.of(particle.whileMoving()) : Optional.empty())));
	}

	private record SoundSpec(Holder<SoundEvent> sound, Optional<Double> volume, Optional<Double> pitch, Optional<Long> interval, Perk.Target target) {}

	private static MapCodec<Perk.Sound> sound(Perk.Trigger trigger) {
		return RecordCodecBuilder.<SoundSpec>mapCodec(i -> i.group(
				field("sound", SOUND).forGetter(SoundSpec::sound),
				optionalField("volume", NUMBER).forGetter(SoundSpec::volume),
				optionalField("pitch", NUMBER).forGetter(SoundSpec::pitch),
				optionalField("interval", INTERVAL).forGetter(SoundSpec::interval),
				target(trigger, "sound", sideTargets(trigger)).forGetter(SoundSpec::target)
		).apply(i, SoundSpec::new)).flatXmap(spec -> {
			float volume = spec.volume().orElse(1.0).floatValue(), pitch = spec.pitch().orElse(1.0).floatValue();
			if (volume <= 0 || pitch <= 0) return DataResult.error(() -> "volume and pitch must be more than 0");
			return interval(trigger, spec.interval(), SOUND_INTERVAL).map(interval -> new Perk.Sound(spec.sound(), volume, pitch, interval, spec.target()));
		}, sound -> DataResult.success(new SoundSpec(sound.sound(), Optional.of((double) sound.volume()), Optional.of((double) sound.pitch()),
				trigger.lasting() ? Optional.of((long) sound.intervalTicks()) : Optional.empty(), sound.target())));
	}

	/** A lasting aura's or sound's interval in ticks, {@code fallback} if left out; an event's is 0, and it takes none. */
	private static DataResult<Integer> interval(Perk.Trigger trigger, Optional<Long> written, int fallback) {
		if (!trigger.lasting()) return written.isPresent() ? DataResult.error(() -> "interval is for held and worn Perks only") : DataResult.success(0);
		return DataResult.success((int) Math.min(written.orElse((long) fallback), Integer.MAX_VALUE));
	}

	/** Ticket 05's table: the effects each Trigger allows. */
	private static Set<String> allowedEffects(Perk.Trigger trigger) {
		return switch (trigger) {
			case HELD, WORN -> Set.of("potion", "attribute", "particle", "sound");
			case HIT, HURT -> Set.of("potion", "fire", "knockback", "heal", "particle", "sound");
			case KILL, BLOCK_BREAK -> Set.of("potion", "heal", "particle", "sound");
		};
	}

	/** A potion is on the holder, except on hit (the victim too) and when hurt (the attacker too). */
	private static Set<Perk.Target> potionTargets(Perk.Trigger trigger) {
		return trigger == Perk.Trigger.HIT || trigger == Perk.Trigger.HURT ? Set.of(Perk.Target.SELF, Perk.Target.OTHER) : Set.of(Perk.Target.SELF);
	}

	/**
	 * Particles and sounds: around the holder while lasting; at the target on hit, at the victim or
	 * block on kill and block break; at the wearer or the attacker when hurt.
	 */
	private static Set<Perk.Target> sideTargets(Perk.Trigger trigger) {
		if (trigger.lasting()) return Set.of(Perk.Target.SELF);
		return trigger == Perk.Trigger.HURT ? Set.of(Perk.Target.SELF, Perk.Target.OTHER) : Set.of(Perk.Target.OTHER);
	}

	/** {@code target}, defaulting to the only one allowed, or to {@code self}. */
	private static MapCodec<Perk.Target> target(Perk.Trigger trigger, String type, Set<Perk.Target> allowed) {
		Perk.Target fallback = allowed.size() == 1 ? allowed.iterator().next() : Perk.Target.SELF;
		return orElse(optionalField("target", TARGET.validate(t -> allowed.contains(t) ? DataResult.success(t) : DataResult.error(() -> "a \"" + trigger.id()
				+ "\" Perk's " + type + " can only target " + allowed.stream().map(Perk.Target::id).sorted().collect(Collectors.joining(" or "))))), fallback);
	}

	/** A registry entry by id; {@code what} names it in the error ("there's no effect …"). */
	private static <T> Codec<Holder<T>> registered(Registry<T> registry, String what) {
		return TEXT.comapFlatMap(id -> {
			Identifier parsed = Identifier.tryParse(id);
			if (parsed == null) return DataResult.error(() -> "\"" + id + "\" is not an id");
			return registry.get(parsed).<DataResult<Holder<T>>>map(DataResult::success).orElseGet(() -> DataResult.error(() -> "there's no " + what + " \"" + id + "\""));
		}, holder -> String.valueOf(registry.getKey(holder.value())));
	}
}
