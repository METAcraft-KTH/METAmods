package nu.metacraft.nether_rifts.mob;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * {@code /rifts showcase}: the rift mobs' looks laid out for staff to look at, in rows in front of the player, facing
 * them: a Piglin Drummer per animation of its model (each looping just that one, as one animation poses the whole
 * model; one-shots start again after a short pause), Piglin Grenadiers with their gloved sword and held at each stage of
 * a throw (the bomb in the fist, then let go), and a vanilla piglin in iron to compare with; then Piglin Drummers in each
 * kind of armour with gear in hand or on the head (named after it); behind them, a Chained Ghast per animation of its
 * model (as the drummers, each over idle as {@link WarGhast#play} plays it), with its kit and diamond armour. Names show
 * what each is.
 *
 * <p>Showcase mobs stand still and silent (no AI, no sounds, no drumming, no bolts), never take damage or despawn,
 * belong to no rift, and are tagged {@link #TAG} so {@code /rifts showcase clear} (or a new showcase) takes them away.
 */
public final class Showcase {

	public static final String TAG = "nether_rifts.showcase";
	/** On a showcase drummer or ghast: the animation it loops. */
	private static final String ANIMATION_TAG = TAG + ".animation.";
	/** On a showcase Grenadier: the stage of a throw its sword is held at (not saved on the stack, so set again). */
	private static final String THROW_TAG = TAG + ".throw.";

	/** How long a one-shot animation rests before it plays again, in ticks. */
	public static final int PAUSE = 20;
	/** The order drummer animations are laid out in; others follow by name. */
	private static final List<String> ORDER = List.of("idle", "walk", "beat", "big_beat", "warcry");
	/** The Chained Ghast's animations, in the order they are laid out. */
	private static final List<String> GHAST_ANIMATIONS = List.of(WarGhast.IDLE, WarGhast.SHOOT, WarGhast.DIVE, WarGhast.CLIMB,
			WarGhast.DROP_BOMB);
	/** Across a row, per mob (room for its name), and how far ahead each row is, in blocks. */
	private static final double SPACING = 2.5;
	private static final double ARMOUR_SPACING = 2.2;
	private static final double GHAST_SPACING = 5;
	public static final double FRONT = 5;
	public static final double MIDDLE = 8;
	public static final double BACK = 15;
	/** The ground under a spot is looked for from this far over the player's feet. */
	private static final double HEADROOM = 3;

	/** The showcase mobs loaded now, and the game time each one-shot may start again (absent: playing or due now). */
	private static final Map<Mob, Long> LIVE = Collections.synchronizedMap(new WeakHashMap<>());

	private Showcase() {}

	public static void init() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob mob && isShowcase(mob)) {
				LIVE.put(mob, 0L);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(Showcase::tick);
	}

	public static boolean isShowcase(Entity entity) {
		return entity.entityTags().contains(TAG);
	}

	/** A dyed, an enchanted, a plain set: what a drummer wears and holds. */
	private record Kit(String name, Item helmet, Item chest, Item legs, Item feet, Item mainHand, Item offHand) {}

	private static final List<Kit> ARMOUR = List.of(
			new Kit("leather (dyed red)", Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS, Items.IRON_SWORD, Items.AIR),
			new Kit("chainmail", Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS, Items.GOLDEN_SPEAR, Items.AIR),
			new Kit("iron", Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS, Items.IRON_SWORD, Items.SHIELD),
			new Kit("gold", Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS, Items.GOLDEN_SWORD, Items.AIR),
			new Kit("diamond (glint)", Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS, Items.DIAMOND_SWORD, Items.AIR),
			new Kit("netherite", Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS, Items.NETHERITE_SPEAR, Items.SHIELD),
			new Kit("copper", Items.COPPER_HELMET, Items.COPPER_CHESTPLATE, Items.COPPER_LEGGINGS, Items.COPPER_BOOTS, Items.COPPER_SWORD, Items.AIR),
			new Kit("turtle helmet", Items.TURTLE_HELMET, Items.AIR, Items.AIR, Items.AIR, Items.CROSSBOW, Items.AIR),
			new Kit("carved pumpkin", Items.CARVED_PUMPKIN, Items.IRON_CHESTPLATE, Items.AIR, Items.AIR, Items.AIR, Items.SHIELD));
	private static final int LEATHER_RED = 0xB02E26;

	/**
	 * Clears any showcase, then lays a new one out in front of {@code at}, looking along {@code yaw}: rows {@link #FRONT},
	 * {@link #MIDDLE} and (the ghasts) {@link #BACK} blocks ahead. Returns what it spawned.
	 */
	public static List<Mob> spawn(ServerLevel level, Vec3 at, float yaw) {
		clear(level.getServer());
		var ahead = Vec3.directionFromRotation(0, yaw);
		var right = new Vec3(-ahead.z, 0, ahead.x);
		float facing = Mth.wrapDegrees(yaw + 180);
		var spawned = new ArrayList<Mob>();

		var front = new ArrayList<Mob>();
		var animations = new ArrayList<>(RiftModels.WAR_DRUMMER.animations().keySet());
		animations.sort((a, b) -> {
			int i = ORDER.indexOf(a), j = ORDER.indexOf(b);
			return i >= 0 && j >= 0 ? Integer.compare(i, j) : i >= 0 ? -1 : j >= 0 ? 1 : a.compareTo(b);
		});
		for (var animation : animations) {
			var drummer = create(level, RiftEntities.WAR_DRUMMER, "Drummer: " + animation);
			drummer.addTag(ANIMATION_TAG + animation);
			front.add(drummer);
		}
		front.add(create(level, RiftEntities.GHAST_BOMBARDIER, "Piglin Grenadier"));
		for (var stage : GrenadierSword.Throw.values()) {
			var grenadier = create(level, RiftEntities.GHAST_BOMBARDIER, stage == GrenadierSword.Throw.BOMB ? "Grenadier: bomb in hand" : "Grenadier: bomb thrown");
			grenadier.addTag(THROW_TAG + stage.name());
			GrenadierSword.setThrow(grenadier.getMainHandItem(), stage);
			front.add(grenadier);
		}
		var piglin = create(level, EntityTypes.PIGLIN, "vanilla piglin: iron");
		dress(piglin, ARMOUR.get(2));
		front.add(piglin);
		place(level, front, at.add(ahead.scale(FRONT)), right, SPACING, facing, 0);
		spawned.addAll(front);

		var armoured = new ArrayList<Mob>();
		var enchantments = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		for (var kit : ARMOUR) {
			var drummer = create(level, RiftEntities.WAR_DRUMMER, kit.name());
			dress(drummer, kit);
			if (kit.helmet() == Items.LEATHER_HELMET) {
				for (var slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
					drummer.getItemBySlot(slot).set(DataComponents.DYED_COLOR, new DyedItemColor(LEATHER_RED));
				}
			}
			if (kit.helmet() == Items.DIAMOND_HELMET) {
				for (var slot : EquipmentSlot.values()) {
					var stack = drummer.getItemBySlot(slot);
					if (!stack.isEmpty()) {
						stack.enchant(enchantments.getOrThrow(slot == EquipmentSlot.MAINHAND ? Enchantments.SHARPNESS : Enchantments.PROTECTION), 4);
					}
				}
			}
			armoured.add(drummer);
		}
		place(level, armoured, at.add(ahead.scale(MIDDLE)), right, ARMOUR_SPACING, facing, 0);
		spawned.addAll(armoured);

		var ghasts = new ArrayList<Mob>();
		for (var animation : GHAST_ANIMATIONS) {
			var ghast = create(level, RiftEntities.WAR_GHAST, "Chained Ghast: " + animation);
			ghast.addTag(ANIMATION_TAG + animation);
			// diamond on the spear piglin
			set(ghast, EquipmentSlot.CHEST, Items.DIAMOND_CHESTPLATE);
			set(ghast, EquipmentSlot.LEGS, Items.DIAMOND_LEGGINGS);
			set(ghast, EquipmentSlot.FEET, Items.DIAMOND_BOOTS);
			ghasts.add(ghast);
		}
		// its deck clear of the ground (it lifts itself clear, if need be, as it spawns)
		place(level, ghasts, at.add(ahead.scale(BACK)), right, GHAST_SPACING, facing, WarGhast.DECK_DROP + 0.5);
		spawned.addAll(ghasts);

		for (var mob : spawned) {
			if (mob instanceof WarGhast ghast) {
				ghast.finalizeSpawn(level, level.getCurrentDifficultyAt(ghast.blockPosition()), EntitySpawnReason.COMMAND, null);
			}
			level.addFreshEntity(mob);
			LIVE.put(mob, 0L);
		}
		return spawned;
	}

	/** A showcase mob: still, silent, unhurt, never despawning, named. */
	private static <T extends Mob> T create(ServerLevel level, EntityType<T> type, String name) {
		var mob = type.create(level, EntitySpawnReason.COMMAND);
		if (mob == null) {
			throw new IllegalStateException("Couldn't make a " + type);
		}
		mob.addTag(TAG);
		mob.setNoAi(true);
		mob.setSilent(true);
		mob.setPermanentlyInvulnerable(true);
		mob.setPersistenceRequired();
		mob.setCanPickUpLoot(false);
		mob.setCustomName(Component.literal(name));
		mob.setCustomNameVisible(true);
		for (var slot : EquipmentSlot.values()) {
			mob.setDropChance(slot, 0f);
		}
		if (mob instanceof AbstractPiglin piglin) {
			piglin.setImmuneToZombification(true);
		}
		return mob;
	}

	private static void dress(Mob mob, Kit kit) {
		set(mob, EquipmentSlot.HEAD, kit.helmet());
		set(mob, EquipmentSlot.CHEST, kit.chest());
		set(mob, EquipmentSlot.LEGS, kit.legs());
		set(mob, EquipmentSlot.FEET, kit.feet());
		set(mob, EquipmentSlot.MAINHAND, kit.mainHand());
		set(mob, EquipmentSlot.OFFHAND, kit.offHand());
	}

	private static void set(Mob mob, EquipmentSlot slot, Item item) {
		mob.setItemSlot(slot, item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item));
	}

	/**
	 * Mobs in a row centred on {@code centre}, on the ground (or {@code lift} above it), facing {@code facing}. The ground
	 * is the first solid block under a spot a few blocks over the player's feet, not the world's top (the nether's
	 * roof, a cave's ceiling).
	 */
	private static void place(ServerLevel level, List<Mob> row, Vec3 centre, Vec3 right, double spacing, float facing, double lift) {
		for (int i = 0; i < row.size(); i++) {
			var at = centre.add(right.scale((i - (row.size() - 1) / 2.0) * spacing));
			var under = DeckFloatGoal.groundUnder(level, at.add(0, HEADROOM, 0));
			double ground = under == null ? at.y : under;
			var mob = row.get(i);
			mob.snapTo(at.x, ground + lift, at.z, facing, 0);
			mob.setYHeadRot(facing);
			mob.setYBodyRot(facing);
		}
	}

	/** Takes every showcase mob away (a ghast's crew hitboxes go with it). Returns how many. */
	public static int clear(MinecraftServer server) {
		int n = 0;
		for (var level : server.getAllLevels()) {
			var found = new ArrayList<Entity>();
			for (var entity : level.getAllEntities()) {
				if (isShowcase(entity)) {
					found.add(entity);
				}
			}
			for (var entity : found) {
				entity.discard();
				n++;
			}
		}
		LIVE.clear();
		return n;
	}

	/**
	 * Keeps each drummer and ghast on its animation (a one-shot again after {@link #PAUSE} ticks) and each Grenadier's
	 * sword at its throw.
	 */
	private static void tick(MinecraftServer server) {
		List<Mob> mobs;
		synchronized (LIVE) {
			LIVE.keySet().removeIf(Entity::isRemoved);
			mobs = List.copyOf(LIVE.keySet());
		}
		for (var mob : mobs) {
			if (mob instanceof GhastBombardier grenadier) {
				var stage = throwStage(grenadier);
				if (stage != null) {
					GrenadierSword.setThrow(grenadier.getMainHandItem(), stage);
				}
				continue;
			}
			var animation = animation(mob);
			if (animation == null || animation.equals("idle")) {
				continue;
			}
			var animator = mob instanceof WarDrummer drummer ? drummer.getHolder().getAnimator()
					: mob instanceof WarGhast ghast ? ghast.getHolder().getAnimator() : null;
			if (animator == null || animator.isPlaying(animation)) {
				continue;
			}
			long now = mob.level().getGameTime();
			long due = LIVE.getOrDefault(mob, 0L);
			if (due == 0L) {
				// just ended (or never started): play it now the first time, after a pause from then on
				due = mob.tickCount < 5 ? now : now + PAUSE;
				LIVE.put(mob, due);
			}
			if (now >= due) {
				if (mob instanceof WarGhast ghast) {
					ghast.play(animation);
				} else {
					animator.playAnimation(animation, 10, true);
				}
				LIVE.put(mob, 0L);
			}
		}
	}

	/** The stage of a throw a showcase Grenadier is held at, or null (holding its sword, or another mob). */
	@Nullable
	public static GrenadierSword.Throw throwStage(Mob mob) {
		for (var tag : mob.entityTags()) {
			if (tag.startsWith(THROW_TAG)) {
				return GrenadierSword.Throw.valueOf(tag.substring(THROW_TAG.length()));
			}
		}
		return null;
	}

	/** The animation a showcase drummer or ghast loops, or null for another mob. */
	@Nullable
	public static String animation(Mob mob) {
		for (var tag : mob.entityTags()) {
			if (tag.startsWith(ANIMATION_TAG)) {
				return tag.substring(ANIMATION_TAG.length());
			}
		}
		return null;
	}

	/** The showcase mobs loaded now. */
	public static Set<Mob> live() {
		synchronized (LIVE) {
			return Set.copyOf(LIVE.keySet());
		}
	}
}
