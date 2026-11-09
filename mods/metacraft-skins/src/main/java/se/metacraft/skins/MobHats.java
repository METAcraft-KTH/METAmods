package se.metacraft.skins;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.gamerules.GameRules;
import se.metacraft.skins.item.MobHat;
import se.metacraft.skins.item.SkinBox;

import java.util.List;
import java.util.Set;

/**
 * Mob Hats: now and then one of {@link #MOBS} spawns wearing a random hat from the Skin Box
 * {@link #HATS_FROM}, and when it dies it drops a {@link #DROPS} box, never the hat. The chance is
 * the {@code mob-hat-chance} setting ({@link SkinsSettings}, set in game with {@code /itemskins spawnchance})
 * at local difficulty 0, falling in a straight line to nothing at {@link #NO_HATS_AT}. Every spawn
 * counts, spawners and farms included. A hat is for good: the mob never swaps it for an item it picks
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
	/** The local difficulty where the chance reaches 0: vanilla's highest. */
	public static final float NO_HATS_AT = 6.75f;

	private MobHats() {}

	/**
	 * The chance (0 to 1) a mob spawning at this local difficulty wears a hat: {@code basePercent}
	 * percent at 0, falling linearly to 0 at {@link #NO_HATS_AT}.
	 */
	public static double chance(double basePercent, float localDifficulty) {
		return basePercent / 100 * Math.clamp(1 - localDifficulty / NO_HATS_AT, 0.0, 1.0);
	}

	/**
	 * A mob has spawned (any way: naturally, from a spawner or egg, by command): one of {@link #MOBS}
	 * puts on a hat by {@link #chance}, from the {@code mob-hat-chance} setting as it is now.
	 */
	public static void spawned(Skins skins, Mob mob, DifficultyInstance difficulty) {
		if (!MOBS.contains(mob.getType())) return;
		if (mob.getRandom().nextDouble() >= chance(skins.settings().mobHatChance(), difficulty.getEffectiveDifficulty())) return;
		putOnHat(skins, mob);
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
	 * An entity is dying, before its loot drops: one wearing a hat drops one {@link #DROPS} box, a
	 * baby too, unless the {@code mobDrops} game rule is off or the box is unknown or Dormant.
	 */
	public static void died(Skins skins, LivingEntity entity, ServerLevel level) {
		if (!wearsHat(entity) || !Boolean.TRUE.equals(level.getGameRules().get(GameRules.MOB_DROPS))) return;
		if (skins.box(DROPS).filter(box -> !box.dormant()).isEmpty()) return;
		entity.spawnAtLocation(level, SkinBox.of(DROPS));
	}
}
