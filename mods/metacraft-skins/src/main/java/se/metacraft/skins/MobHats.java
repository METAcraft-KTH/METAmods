package se.metacraft.skins;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.gamerules.GameRules;
import se.metacraft.skins.item.MobHat;
import se.metacraft.skins.item.SkinBox;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Mob Hats: now and then one of {@link #MOBS} spawns wearing a random hat from a Skin Box, and when
 * it dies it drops that box, never the hat. The box is {@link #CLASSIC}, except for one of
 * {@link #WINTER_MOBS} spawning in a {@link #COLD} biome: that one wears a {@link #WINTER} hat and drops
 * a Winter box (or, while the Winter box is Dormant, a Classic one as anywhere else). Each box has its own
 * chance, in the {@code mob-hat-chances} setting ({@link SkinsSettings}, set in game with
 * {@code /itemskins spawnchance <box> <percent>}).
 * When a mob spawns wearing a hat, the 3x3 chunks centred on the one it spawned in go on cooldown for a
 * random {@link #COOLDOWN_MIN} to {@link #COOLDOWN_MAX} of real time ({@link MobHatCooldowns}): there the
 * chance is cut by {@link #COOLDOWN_CUT}. If that mob despawns, the cooldown it started ends; if it dies,
 * however, the cooldown runs its course. Every spawn counts, spawners and farms included. A hat is for good: the mob never swaps it for an item it picks
 * up, and like any helmet it keeps a zombie or skeleton from burning in daylight. Change who and which
 * boxes with the constants below. The mod's
 * mixins on {@code Mob.finalizeSpawn}, {@code Mob.checkDespawn}, {@code Mob.canReplaceCurrentItem} and
 * {@code LivingEntity.dropAllDeathLoot} call in here or check {@link #wearsHat}.
 */
public final class MobHats {
	/** The mobs that can spawn wearing a hat. Exact types: a drowned or wither skeleton, say, is a mob of its own and isn't listed. */
	public static final Set<EntityType<?>> MOBS = Set.of(
			EntityTypes.ZOMBIE, EntityTypes.HUSK, EntityTypes.ZOMBIE_VILLAGER,
			EntityTypes.SKELETON, EntityTypes.STRAY, EntityTypes.BOGGED, EntityTypes.PARCHED,
			EntityTypes.CREEPER);
	/** The mobs that wear a {@link #WINTER} hat, and drop that box, when they spawn in a {@link #COLD} biome. */
	public static final Set<EntityType<?>> WINTER_MOBS = Set.of(EntityTypes.ZOMBIE, EntityTypes.SKELETON, EntityTypes.STRAY, EntityTypes.CREEPER);
	/** The cold biomes: the common {@code c:is_cold/overworld} tag (taigas, snowy and frozen biomes, windswept hills, cold oceans…), which a datapack can change. */
	public static final TagKey<Biome> COLD = TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("c", "is_cold/overworld"));
	/** The Skin Box (its id, its file name) most hats come from and a hat-wearing mob drops: each of its hats as likely as the next, its weights ignored. */
	public static final String CLASSIC = "classic";
	/** The Skin Box the hats come from, and a mob drops, for {@link #WINTER_MOBS} in a {@link #COLD} biome. */
	public static final String WINTER = "winter";
	/** Every Skin Box a mob can take its hat from, each with its own chance in {@code mob-hat-chances}. */
	public static final List<String> BOXES = List.of(CLASSIC, WINTER);
	/** How much less likely a hat is in a chunk on cooldown: 99% less. */
	public static final double COOLDOWN_CUT = 0.99;
	/** The shortest and longest a cooldown lasts; each hat-wearing spawn picks a length in between, to the second. */
	public static final Duration COOLDOWN_MIN = Duration.ofHours(1), COOLDOWN_MAX = Duration.ofHours(6);

	private MobHats() {}

	/**
	 * The chance (0 to 1) a mob spawning wears a hat: {@code basePercent} percent, cut by
	 * {@link #COOLDOWN_CUT} where the chunk is on cooldown.
	 */
	public static double chance(double basePercent, boolean onCooldown) {
		return basePercent / 100 * (onCooldown ? 1 - COOLDOWN_CUT : 1);
	}

	/** The box a mob of this type spawning where it is or isn't cold takes its hat from: {@link #WINTER} or {@link #CLASSIC}. */
	public static String boxFor(EntityType<?> type, boolean cold) {
		return cold && WINTER_MOBS.contains(type) ? WINTER : CLASSIC;
	}

	/**
	 * A mob has spawned (any way: naturally, from a spawner or egg, by command): one of {@link #MOBS}
	 * would wear a hat from the box {@link #boxFor} the biome it's in, or from the Classic box while that
	 * one is Dormant or missing. It puts one on by {@link #chance}, from that box's chance in the
	 * {@code mob-hat-chances} setting as it is now and whether its chunk is on cooldown at {@code now}.
	 * One that does puts the chunks around it that aren't on cooldown yet on cooldown for
	 * {@link #cooldownLength}, as their owner.
	 */
	public static void spawned(Skins skins, Mob mob, Instant now) {
		if (!MOBS.contains(mob.getType())) return;
		String box = boxFor(mob.getType(), mob.level().getBiome(mob.blockPosition()).is(COLD));
		if (skins.box(box).filter(it -> !it.dormant()).isEmpty()) box = CLASSIC;
		var dimension = mob.level().dimension();
		boolean cooldown = skins.mobHatCooldowns().onCooldown(dimension, mob.getBlockX(), mob.getBlockZ(), now);
		if (mob.getRandom().nextDouble() >= chance(skins.settings().mobHatChance(box), cooldown)) return;
		if (!putOnHat(skins, mob, box) && !putOnHat(skins, mob, CLASSIC)) return;
		skins.mobHatCooldowns().start(dimension, mob.getBlockX(), mob.getBlockZ(), mob.getUUID(), cooldownLength(mob.getRandom()), now);
	}

	/**
	 * A mob has despawned (gone too far from every player, or the world went Peaceful), not died: the
	 * cooldown its spawn started ends, if it started one. Its death leaves the cooldown alone.
	 */
	public static void despawned(Skins skins, Mob mob) {
		if (wearsHat(mob)) skins.mobHatCooldowns().end(mob.getUUID());
	}

	/** A cooldown's length: {@link #COOLDOWN_MIN} to {@link #COOLDOWN_MAX}, each second as likely. */
	public static Duration cooldownLength(RandomSource random) {
		long min = COOLDOWN_MIN.toSeconds(), max = COOLDOWN_MAX.toSeconds();
		return Duration.ofSeconds(min + random.nextInt((int) (max - min + 1)));
	}

	/** {@link #putOnHat(Skins, Mob, String)} from the {@link #CLASSIC} box. */
	public static boolean putOnHat(Skins skins, Mob mob) {
		return putOnHat(skins, mob, CLASSIC);
	}

	/**
	 * Puts a random hat from {@link #hats} of the box on the mob's head, in place of anything there,
	 * never to drop; the hat remembers the box, for {@link #died}. Returns false, doing nothing, while
	 * there are no hats to pick from.
	 */
	public static boolean putOnHat(Skins skins, Mob mob, String box) {
		List<String> hats = hats(skins, box);
		if (hats.isEmpty()) return false;
		mob.setItemSlot(EquipmentSlot.HEAD, MobHat.of(hats.get(mob.getRandom().nextInt(hats.size())), box));
		mob.setDropChance(EquipmentSlot.HEAD, 0);
		return true;
	}

	/** The hats a mob can wear from the {@link #CLASSIC} box. */
	public static List<String> hats(Skins skins) {
		return hats(skins, CLASSIC);
	}

	/** The hats a mob can wear from a box: its usable skins that are hats. None while the box is unknown or Dormant. */
	public static List<String> hats(Skins skins, String box) {
		return skins.box(box).filter(it -> !it.dormant())
				.map(it -> it.entries().stream().map(BoxSummary.Entry::skin)
						.filter(id -> skins.skin(id).flatMap(SkinSummary::kind).filter(Kind::isHat).isPresent()).toList())
				.orElse(List.of());
	}

	/** Is it wearing a Mob Hat? */
	public static boolean wearsHat(LivingEntity entity) {
		return MobHat.skinOf(entity.getItemBySlot(EquipmentSlot.HEAD)).isPresent();
	}

	/**
	 * An entity is dying, before its loot drops: one wearing a hat, however it died, drops one box,
	 * the one its hat came from ({@link #CLASSIC} for a hat from before hats remembered), a baby too,
	 * unless the {@code mobDrops} game rule is off or the box is unknown or Dormant. Its cooldown, if it
	 * started one, runs on.
	 */
	public static void died(Skins skins, LivingEntity entity, ServerLevel level) {
		if (!wearsHat(entity)) return;
		if (!Boolean.TRUE.equals(level.getGameRules().get(GameRules.MOB_DROPS))) return;
		String drops = MobHat.boxOf(entity.getItemBySlot(EquipmentSlot.HEAD)).orElse(CLASSIC);
		if (skins.box(drops).filter(box -> !box.dormant()).isEmpty()) return;
		entity.spawnAtLocation(level, SkinBox.of(drops));
	}
}
