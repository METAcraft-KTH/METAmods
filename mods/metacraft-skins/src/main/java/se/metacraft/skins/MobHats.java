package se.metacraft.skins;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.gamerules.GameRules;
import se.metacraft.skins.item.MobHat;
import se.metacraft.skins.item.SkinBox;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Mob Hats: now and then one of {@link #MOBS} spawns wearing a random hat from the Skin Box
 * {@link #HATS_FROM}, and when it dies it drops a {@link #DROPS} box, never the hat. The chance is
 * the {@code mob-hat-chance} setting ({@link SkinsSettings}, set in game with {@code /itemskins spawnchance}).
 * When a hat-wearing mob dies, the 3x3 chunks centred on the one it died in go on cooldown for a
 * random {@link #COOLDOWN_MIN} to {@link #COOLDOWN_MAX} of real time ({@link MobHatCooldowns}): there the
 * chance is cut by {@link #COOLDOWN_CUT}. Every spawn counts, spawners and farms included. A hat is for good: the mob never swaps it for an item it picks
 * up, and like any helmet it keeps a zombie or skeleton from burning in daylight. Change who and which
 * boxes with the constants below. The mod's
 * mixins on {@code Mob.finalizeSpawn}, {@code Mob.canReplaceCurrentItem} and
 * {@code LivingEntity.dropAllDeathLoot} call in here or check {@link #wearsHat}.
 */
public final class MobHats {
	/** The mobs that can spawn wearing a hat. Exact types: a drowned or wither skeleton, say, is a mob of its own and isn't listed. */
	public static final Set<EntityType<?>> MOBS = Set.of(
			EntityTypes.ZOMBIE, EntityTypes.HUSK, EntityTypes.ZOMBIE_VILLAGER,
			EntityTypes.SKELETON, EntityTypes.STRAY, EntityTypes.BOGGED, EntityTypes.PARCHED,
			EntityTypes.CREEPER);
	/** The Skin Box (its id, its file name) the hats come from: each of its hats as likely as the next, its weights ignored. */
	public static final String HATS_FROM = "classic";
	/** The Skin Box (its id) a hat-wearing mob drops when it dies. */
	public static final String DROPS = "classic";
	/** How much less likely a hat is in a chunk on cooldown: 99% less. */
	public static final double COOLDOWN_CUT = 0.99;
	/** The shortest and longest a cooldown lasts; each death picks a length in between, to the second. */
	public static final Duration COOLDOWN_MIN = Duration.ofHours(1), COOLDOWN_MAX = Duration.ofHours(6);

	private MobHats() {}

	/**
	 * The chance (0 to 1) a mob spawning wears a hat: {@code basePercent} percent, cut by
	 * {@link #COOLDOWN_CUT} where the chunk is on cooldown.
	 */
	public static double chance(double basePercent, boolean onCooldown) {
		return basePercent / 100 * (onCooldown ? 1 - COOLDOWN_CUT : 1);
	}

	/**
	 * A mob has spawned (any way: naturally, from a spawner or egg, by command): one of {@link #MOBS}
	 * puts on a hat by {@link #chance}, from the {@code mob-hat-chance} setting as it is now and
	 * whether its chunk is on cooldown at {@code now}.
	 */
	public static void spawned(Skins skins, Mob mob, Instant now) {
		if (!MOBS.contains(mob.getType())) return;
		boolean cooldown = skins.mobHatCooldowns().onCooldown(mob.level().dimension(), mob.getBlockX(), mob.getBlockZ(), now);
		if (mob.getRandom().nextDouble() >= chance(skins.settings().mobHatChance(), cooldown)) return;
		putOnHat(skins, mob);
	}

	/** A cooldown's length: {@link #COOLDOWN_MIN} to {@link #COOLDOWN_MAX}, each second as likely. */
	public static Duration cooldownLength(RandomSource random) {
		long min = COOLDOWN_MIN.toSeconds(), max = COOLDOWN_MAX.toSeconds();
		return Duration.ofSeconds(min + random.nextInt((int) (max - min + 1)));
	}

	/**
	 * Puts a random hat from {@link #hats} on the mob's head, in place of anything there, never to drop.
	 * Returns false, doing nothing, while there are no hats to pick from.
	 */
	public static boolean putOnHat(Skins skins, Mob mob) {
		List<String> hats = hats(skins);
		if (hats.isEmpty()) return false;
		mob.setItemSlot(EquipmentSlot.HEAD, MobHat.of(hats.get(mob.getRandom().nextInt(hats.size()))));
		mob.setDropChance(EquipmentSlot.HEAD, 0);
		return true;
	}

	/** The hats a mob can wear: the usable skins of {@link #HATS_FROM} that are hats. None while the box is unknown or Dormant. */
	public static List<String> hats(Skins skins) {
		return skins.box(HATS_FROM).filter(box -> !box.dormant())
				.map(box -> box.entries().stream().map(BoxSummary.Entry::skin)
						.filter(id -> skins.skin(id).flatMap(SkinSummary::kind).filter(Kind::isHat).isPresent()).toList())
				.orElse(List.of());
	}

	/** Is it wearing a Mob Hat? */
	public static boolean wearsHat(LivingEntity entity) {
		return MobHat.skinOf(entity.getItemBySlot(EquipmentSlot.HEAD)).isPresent();
	}

	/**
	 * An entity is dying, before its loot drops: one wearing a hat, however it died, puts the 3x3
	 * chunks around it on cooldown for {@link #cooldownLength} from {@code now}, and drops one
	 * {@link #DROPS} box, a baby too, unless the {@code mobDrops} game rule is off or the box is
	 * unknown or Dormant.
	 */
	public static void died(Skins skins, LivingEntity entity, ServerLevel level, Instant now) {
		if (!wearsHat(entity)) return;
		skins.mobHatCooldowns().start(level.dimension(), entity.getBlockX(), entity.getBlockZ(), cooldownLength(entity.getRandom()), now);
		if (!Boolean.TRUE.equals(level.getGameRules().get(GameRules.MOB_DROPS))) return;
		if (skins.box(DROPS).filter(box -> !box.dormant()).isEmpty()) return;
		entity.spawnAtLocation(level, SkinBox.of(DROPS));
	}
}
