package nu.metacraft.nether_rifts.mob;

import de.tomalbrc.bil.api.AnimatedEntity;
import de.tomalbrc.bil.core.component.AnimationComponent;
import de.tomalbrc.bil.core.holder.wrapper.Bone;
import de.tomalbrc.bil.core.holder.wrapper.ItemBone;
import de.tomalbrc.bil.core.holder.wrapper.Locator;
import de.tomalbrc.bil.core.model.Node;
import de.tomalbrc.bil.core.model.Pose;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.nether_rifts.NetherRiftsConfig;
import nu.metacraft.nether_rifts.raid.RiftMobs;
import nu.metacraft.nether_rifts.raid.Slot;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * The Chained Ghast: a ghast in a harness with a deck slung under it on chains, crewed by a Piglin Drummer, a Piglin
 * Grenadier and two piglins (one with a spear, one with a crossbow). One model (war_ghast.bbmodel, the artist's, brought
 * in by tools/import_ghast.py), crew and all. It floats as a ghast does, keeping its deck clear of the ground and
 * itself no more than {@code max_float_height} over it, and goes after players below it. Its crew work it: the spear
 * piglin stabs it into shooting fireballs, the Grenadier drops magma bombs from the deck on bombing runs, the crossbow
 * piglin shoots bolts at players it can see, and the drummer drums in a fight, his War Beat reaching the piglins under
 * the ghast. When it dies the crew jump off as real mobs and glide down.
 *
 * <p>Each crew member has a hitbox of its own ({@link CrewHitbox}) where its figure stands, and the deck one that
 * projectiles glance off ({@link DeckHitbox}). A crew member killed on the deck is gone from the model with its gear,
 * its work stops (no fireballs without the spear piglin, no bombing runs without the Grenadier, no bolts without the
 * crossbow piglin, no drumming without the drummer) and it doesn't jump when the ghast dies. The rest jump with
 * the health they had left.
 *
 * <p>Its gear shows on the crew ({@link HeldItems}, {@link ArmourPieces}): the main hand in the spear piglin's left
 * hand, the off hand in the crossbow piglin's right (the ghast is left-handed), the head slot on the Grenadier's head (worn armour on the spear
 * piglin's instead), and chest, legs and feet armour on the spear piglin. Every Chained Ghast gets a golden spear, a
 * crossbow and the Grenadier Goggles, none of which drop. The armour is for show on the ghast (no armour points,
 * toughness or enchantment effects), and protects the spear piglin's hitbox. The crew take the gear with them when
 * they jump.
 */
public class WarGhast extends Ghast implements AnimatedEntity {

	/** How far the deck's underside hangs below the ghast's feet: the model's lowest cube, 94 units under them. */
	public static final double DECK_DROP = 94 / 16.0;
	/** The deck floor's size: the model's, 48 units square and 4 thick. */
	public static final double DECK_WIDTH = 3;
	public static final double DECK_THICKNESS = 0.25;

	/** Crew glide time: enough to come down from a bombing run's climb and well beyond. */
	public static final int CREW_SLOW_FALL_TICKS = 200;

	/** Default for {@link NetherRiftsConfig.BombardierTuning#maxFloatHeight}. */
	public static final double MAX_FLOAT_HEIGHT = 20;
	/** Default for {@link NetherRiftsConfig.CrewTuning#crossbowEvery}. */
	public static final int CROSSBOW_EVERY = 60;
	/** The crossbow piglin shoots at players it can see this close, in blocks. */
	public static final double CROSSBOW_RANGE = 24;
	/** The deck's drummer drums while a player who can fight is this close, in blocks. */
	public static final double DRUM_RANGE = 24;
	/** It loads (and is heard loading) this long before it shoots: a second. */
	public static final int CROSSBOW_LOAD_TICKS = 20;

	/** The faces: one shows at a time. */
	public static final String FACE = "face_sad";
	public static final String SHOOTING_FACE = "face_shooting";

	/** The armour slots, worn for show only. */
	private static final List<EquipmentSlot> ARMOUR = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);

	/**
	 * The figures on the deck: their group in the model, and the mob each becomes. The import marks where each stands
	 * ({@code <figure>_feet}) and which way it faces ({@code <figure>_facing}, a little ahead of its feet).
	 */
	public enum Crew {
		DRUMMER("drummer_piglin"),
		SPEAR("piglin1"),
		CROSSBOW("piglin2"),
		BOMBARDIER("bombardier");

		public final String figure;

		Crew(String figure) {
			this.figure = figure;
		}

		/** The mob it is, and becomes when it jumps. */
		public EntityType<? extends Mob> type() {
			return switch (this) {
				case DRUMMER -> RiftEntities.WAR_DRUMMER;
				case BOMBARDIER -> RiftEntities.GHAST_BOMBARDIER;
				case SPEAR, CROSSBOW -> EntityTypes.PIGLIN;
			};
		}
	}

	/** Where a figure stands in the world, and the yaw it faces. */
	public record Spot(Vec3 feet, float yaw) {}

	private final HeldItems.Holder<WarGhast> holder;
	/** Each figure's bones, under its group. */
	private final Map<Crew, List<Bone<?>>> crewBones = new EnumMap<>(Crew.class);
	/** Killed on the deck. */
	private final EnumSet<Crew> crewDead = EnumSet.noneOf(Crew.class);
	/** The crew's health left (saved with the ghast; their hitboxes aren't); full when missing. */
	private final Map<Crew, Float> crewHealth = new EnumMap<>(Crew.class);
	private final Map<Crew, CrewHitbox> hitboxes = new EnumMap<>(Crew.class);
	@Nullable
	private DeckHitbox deck;
	/** The colour each figure's bones were last given, and the ghast's. */
	private final Map<Crew, Integer> figureTint = new EnumMap<>(Crew.class);
	private int tint = -1;
	private int crossbowClock = 0;
	/** The crew have jumped. */
	private boolean crewGone = false;

	public WarGhast(EntityType<? extends WarGhast> type, Level level) {
		super(type, level);
		this.holder = new HeldItems.Holder<>(this, RiftModels.WAR_GHAST) {
			// the deck hangs below the box BIL culls the bones by: give them room down to its underside
			@Override
			protected void updateCullingBox() {
				float width = getScale() * dimensions.width() * 2;
				float height = -dimensions.height() - (float) DECK_DROP - 1;
				for (var bone : bones) {
					bone.element().setDisplaySize(width, height);
				}
			}

			// BIL shows every bone again whenever the ghast's flags change
			@Override
			protected void updateInvisibility(boolean invisible) {
				super.updateInvisibility(invisible);
				showParts();
			}
		};
		for (var crew : Crew.values()) {
			var bones = new ArrayList<Bone<?>>();
			for (var bone : holder.getBones()) {
				if (inside(bone.node(), crew.figure)) {
					bones.add(bone);
				}
			}
			crewBones.put(crew, bones);
		}
		EntityAttachment.ofTicking(this.holder, this);
		this.holder.getAnimator().playAnimation("idle", 0, true);
		showParts();
		keepKit();
	}

	@Override
	public HeldItems.Holder<WarGhast> getHolder() {
		return holder;
	}

	/**
	 * Vanilla's ghast goals, but floating about with its deck in mind ({@link DeckFloatGoal}), and going after players
	 * below it as far down as it floats: vanilla's ghast only targets players within 4 blocks of its own height, and the
	 * deck keeps this one higher than that over the ground. A bombing run of its own.
	 */
	@Override
	protected void registerGoals() {
		super.registerGoals();
		goalSelector.removeAllGoals(goal -> goal instanceof Ghast.RandomFloatAroundGoal);
		goalSelector.addGoal(5, new DeckFloatGoal(this));
		goalSelector.addGoal(4, new BombingRunGoal(this));
		targetSelector.removeAllGoals(goal -> goal instanceof NearestAttackableTargetGoal<?>);
		targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, (target, level) -> {
			double below = getY() - target.getY();
			return below >= -4 && below <= NetherRiftsConfig.getInstance().mobs().bombardier().maxFloatHeight() + 4;
		}));
	}

	/** Whether the ghast and its deck fit with its feet at {@code at}, clear of blocks. */
	public boolean fitsAt(Vec3 at) {
		return level().noBlockCollision(this, stackBox().move(at.subtract(position())));
	}

	/**
	 * Lifts the ghast till it and its deck are clear of blocks (a spawn egg or a summon puts its feet near the ground, and
	 * the deck under it), up to as far as the deck hangs and a bit: whatever room there is. Returns whether it's clear.
	 */
	private boolean unstick() {
		if (fitsAt(position())) {
			return true;
		}
		for (double up = 0.25; up <= DECK_DROP + 2; up += 0.25) {
			var at = position().add(0, up, 0);
			if (fitsAt(at)) {
				setPos(at);
				setDeltaMovement(getDeltaMovement().multiply(1, 0, 1));
				return true;
			}
		}
		return false;
	}

	/** Whether a node is the model's group {@code name} or inside it. */
	private static boolean inside(@Nullable Node node, String name) {
		for (; node != null; node = node.parent()) {
			if (node.name().equals(name)) {
				return true;
			}
		}
		return false;
	}

	/** A figure's bones, armour and all. */
	public List<Bone<?>> crewBones(Crew crew) {
		return crewBones.get(crew);
	}

	/** Whether a crew member is on the deck: not killed there, and not jumped. */
	public boolean hasCrew(Crew crew) {
		return !crewGone && !crewDead.contains(crew);
	}

	/** It shoots fireballs only while the spear piglin is there to stab it (mixin/GhastShootFireballGoalMixin). */
	public boolean shoots() {
		return hasCrew(Crew.SPEAR);
	}

	/** A crew member's hitbox, while it's on the deck and the ghast has made it. */
	@Nullable
	public CrewHitbox hitbox(Crew crew) {
		return hasCrew(crew) ? hitboxes.get(crew) : null;
	}

	@Nullable
	public DeckHitbox deckHitbox() {
		return deck;
	}

	/**
	 * Shows the face for what the ghast is doing and hides the crew that are gone. An invisible ghast keeps every bone
	 * hidden (BIL hides them).
	 */
	private void showParts() {
		if (isInvisible()) {
			return;
		}
		boolean shooting = isCharging();
		var face = holder.getBone(FACE);
		var shootingFace = holder.getBone(SHOOTING_FACE);
		if (face != null && shootingFace != null) {
			face.setInvisible(shooting);
			shootingFace.setInvisible(!shooting);
		}
		crewBones.forEach((crew, bones) -> {
			if (!hasCrew(crew)) {
				bones.forEach(bone -> bone.setInvisible(true));
			}
		});
	}

	/** The ghast's shooting face, while vanilla's fireball goal charges a shot (half a second before it fires). */
	@Override
	public void setCharging(boolean charging) {
		super.setCharging(charging);
		showParts();
	}

	/**
	 * The kit every Chained Ghast has, in any slot left empty while the crew member who shows it is there: the golden
	 * spear the rift's spear piglins carry, a crossbow and the Grenadier Goggles. Its gear never drops (the crew take
	 * it), and it's left-handed: the main hand shows in the spear piglin's left fist ({@code item_left}), the off hand
	 * in the crossbow piglin's right ({@code item_right}). Set again on spawning and loading (a save or
	 * a summon without equipment has none).
	 */
	private void keepKit() {
		if (hasCrew(Crew.SPEAR)) {
			fill(EquipmentSlot.MAINHAND, Items.GOLDEN_SPEAR);
		}
		if (hasCrew(Crew.CROSSBOW)) {
			fill(EquipmentSlot.OFFHAND, Items.CROSSBOW);
		}
		if (hasCrew(Crew.BOMBARDIER)) {
			fill(EquipmentSlot.HEAD, RiftItems.BOMBARDIER_HELMET);
		}
		for (var slot : EquipmentSlot.values()) {
			setDropChance(slot, 0f);
		}
		setLeftHanded(true);
	}

	private void fill(EquipmentSlot slot, Item item) {
		if (getItemBySlot(slot).isEmpty()) {
			setItemSlot(slot, new ItemStack(item));
		}
	}

	/** The slots whose gear a crew member shows: the spear piglin the main hand and the worn armour, and so on. */
	private List<EquipmentSlot> gearOf(Crew crew) {
		var slots = new ArrayList<EquipmentSlot>();
		switch (crew) {
			case SPEAR -> {
				slots.add(EquipmentSlot.MAINHAND);
				for (var slot : ARMOUR) {
					if (ArmourPieces.isWorn(getItemBySlot(slot), slot)) {
						slots.add(slot);
					}
				}
			}
			case CROSSBOW -> slots.add(EquipmentSlot.OFFHAND);
			case BOMBARDIER -> {
				if (!ArmourPieces.isWorn(getItemBySlot(EquipmentSlot.HEAD), EquipmentSlot.HEAD)) {
					slots.add(EquipmentSlot.HEAD);
				}
			}
			case DRUMMER -> {
			}
		}
		return slots;
	}

	@Override
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, @Nullable SpawnGroupData groupData) {
		var data = super.finalizeSpawn(level, difficulty, reason, groupData);
		keepKit();
		unstick();
		return data;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		for (var crew : Crew.values()) {
			if (crewDead.contains(crew)) {
				output.putBoolean(crew.figure + "_dead", true);
			} else if (crewHealth.containsKey(crew)) {
				output.putFloat(crew.figure + "_health", crewHealth.get(crew));
			}
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		for (var crew : Crew.values()) {
			if (input.getBooleanOr(crew.figure + "_dead", false)) {
				crewDead.add(crew);
			}
			float health = input.getFloatOr(crew.figure + "_health", -1);
			if (health >= 0) {
				crewHealth.put(crew, health);
			}
		}
		showParts();
		keepKit();
	}

	/** Whether a slot of a mob is worn for show only: a Chained Ghast's armour. */
	public static boolean wornForShow(LivingEntity entity, EquipmentSlot slot) {
		return entity instanceof WarGhast && ARMOUR.contains(slot);
	}

	/**
	 * Armour put on gives the ghast nothing: its attribute modifiers (armour, toughness, knockback resistance) come
	 * straight off again. Its enchantments do nothing either (mixin/EnchantmentHelperMixin).
	 */
	@Override
	protected Map<EquipmentSlot, ItemStack> collectEquipmentChanges(Map<EquipmentSlot, ItemStack> last) {
		var changed = super.collectEquipmentChanges(last);
		if (changed != null) {
			changed.forEach((slot, stack) -> {
				if (ARMOUR.contains(slot)) {
					stack.forEachModifier(slot, (attribute, modifier) -> {
						var instance = getAttributes().getInstance(attribute);
						if (instance != null) {
							instance.removeModifier(modifier.id());
						}
					});
				}
			});
		}
		return changed;
	}

	/**
	 * The ghast's box stretched down to the deck's underside: what has to fit wherever it goes. The deck and crew
	 * keep within the ghast's footprint.
	 */
	public AABB stackBox() {
		var box = getBoundingBox();
		return box.setMinY(box.minY - DECK_DROP);
	}

	/**
	 * The Grenadier throws a magma bomb off the deck, straight down onto the ground warning, lobbed up first if need
	 * be: the warning shows at least the configured warning time (default {@link BurningPatches#WARN_TICKS}) before
	 * the blast, where the bomb lands. Plays the model's {@code drop_bomb} animation, if it has one. Nothing without the
	 * Grenadier.
	 */
	public void dropBomb(ServerLevel level) {
		if (!hasCrew(Crew.BOMBARDIER)) {
			return;
		}
		holder.getAnimator().playAnimation("drop_bomb", 10, true);
		var from = position().add(0, -DECK_DROP - 0.5, 0);
		var ground = BurningPatches.groundBelow(level, from);
		var bomb = new MagmaBomb(level, this);
		bomb.setPos(from.x, from.y, from.z);
		int warnTicks = NetherRiftsConfig.getInstance().mobs().bombardier().warnTicks();
		bomb.setDeltaMovement(0, MagmaBomb.launchSpeed(from.y - ground.y, warnTicks), 0);
		var rift = RiftMobs.riftOf(this).orElse(null);
		if (rift != null) {
			bomb.setAttached(RiftMobs.RIFT, rift);
		}
		level.addFreshEntity(bomb);
		BurningPatches.warn(level, ground, rift);
		level.playSound(null, from.x, from.y, from.z, SoundEvents.GHAST_SHOOT, SoundSource.HOSTILE, 1.0f, 1.6f);
	}

	/**
	 * A crew member's hitbox died: its figure goes from the model with the gear it showed (which is lost: nothing of
	 * the crew's drops), and what it did stops.
	 */
	void crewDied(Crew crew) {
		if (!hasCrew(crew)) {
			return;
		}
		for (var slot : gearOf(crew)) {
			setItemSlot(slot, ItemStack.EMPTY);
		}
		crewDead.add(crew);
		crewHealth.remove(crew);
		hitboxes.remove(crew);
		showParts();
	}

	/**
	 * The crew still on the deck jump off where they stand in the model, facing as they do, with the health they had
	 * left, gliding down (slow falling also spares them the fall) to fight on foot: the drummer a Piglin Drummer, the
	 * Grenadier a Piglin Grenadier, the spear piglin with the ghast's main hand and armour, the crossbow piglin with its
	 * off hand. Their figures go from the model. A rift ghast's crew join its rift (its wave, its rules, their roles'
	 * loot); a summoned one's are plain mobs.
	 */
	@Override
	public void die(DamageSource source) {
		if (!isRemoved() && !dead && level() instanceof ServerLevel level) {
			for (var mob : crew(level)) {
				level.addFreshEntity(mob);
			}
			crewGone = true;
			discardParts();
			showParts();
		}
		super.die(source);
	}

	private List<Mob> crew(ServerLevel level) {
		var crew = new ArrayList<Mob>();
		for (var figure : Crew.values()) {
			if (!hasCrew(figure)) {
				continue;
			}
			var mob = figure.type().create(level, EntitySpawnReason.EVENT);
			if (mob == null) {
				continue;
			}
			if (figure == Crew.SPEAR) {
				give(mob, EquipmentSlot.MAINHAND, take(EquipmentSlot.MAINHAND, Items.GOLDEN_SPEAR));
				for (var slot : ARMOUR) {
					// the goggles stay with the ghast's Grenadier, who has its own
					if (ArmourPieces.isWorn(getItemBySlot(slot), slot)) {
						give(mob, slot, take(slot, null));
					}
				}
			} else if (figure == Crew.CROSSBOW) {
				give(mob, EquipmentSlot.MAINHAND, take(EquipmentSlot.OFFHAND, Items.CROSSBOW));
			}
			var hitbox = hitboxes.get(figure);
			float health = hitbox != null && hitbox.isAlive() ? hitbox.getHealth() : crewHealth.getOrDefault(figure, mob.getMaxHealth());
			mob.setHealth(Math.min(health, mob.getMaxHealth()));
			if (hitbox != null) {
				// War Beat's buff, and whatever else was on it
				hitbox.getActiveEffects().forEach(effect -> mob.addEffect(new MobEffectInstance(effect)));
			}
			crew.add(jumpOff(mob, spot(figure)));
		}
		// what the crew didn't take (the goggles: the Grenadier has its own) goes with their figures
		for (var slot : EquipmentSlot.values()) {
			setItemSlot(slot, ItemStack.EMPTY);
		}
		return crew;
	}

	/** Takes what's in a slot off the ghast; the fallback when it's empty. */
	private ItemStack take(EquipmentSlot slot, @Nullable Item fallback) {
		var stack = getItemBySlot(slot);
		setItemSlot(slot, ItemStack.EMPTY);
		return stack.isEmpty() && fallback != null ? new ItemStack(fallback) : stack;
	}

	private static void give(Mob mob, EquipmentSlot slot, ItemStack stack) {
		mob.setItemSlot(slot, stack);
		mob.setDropChance(slot, 0f);
	}

	private Mob jumpOff(Mob mob, Spot spot) {
		mob.snapTo(spot.feet().x, spot.feet().y, spot.feet().z, spot.yaw(), 0);
		mob.setYBodyRot(spot.yaw());
		mob.setYHeadRot(spot.yaw());
		mob.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, CREW_SLOW_FALL_TICKS, 0));
		RiftMobs.riftOf(this).ifPresent(rift -> RiftMobs.enlist(mob, rift, Slot.BOMBARDIER));
		return mob;
	}

	/**
	 * Where a figure stands now and the way it faces: its markers' places in the playing animation (else at rest),
	 * turned with the ghast's body as BIL turns the model. The model faces -z and BIL turns it half round, so the
	 * markers' +z is the ghast's front.
	 */
	public Spot spot(Crew crew) {
		var feet = place(crew.figure + "_feet", false);
		var ahead = place(crew.figure + "_facing", false).subtract(feet);
		float turn = (float) Math.toDegrees(Math.atan2(-ahead.x, ahead.z));
		return new Spot(toWorld(feet), Mth.wrapDegrees(yBodyRot + turn));
	}

	/** From the model's space (blocks from the feet, before the body's turn) to the world's. */
	private Vec3 toWorld(Vec3 inModel) {
		return position().add(inModel.scale(holder.getScale()).yRot(-yBodyRot * Mth.DEG_TO_RAD));
	}

	/** A node's place in the model now (in the playing animation, else at rest), or at rest. */
	private Vec3 place(String name, boolean atRest) {
		var model = holder.getModel();
		var node = HeldItems.marker(model, name);
		if (node == null) {
			throw new IllegalStateException("war_ghast.bbmodel has no " + name + ": import it with tools/import_ghast.py");
		}
		Pose rest = model.defaultPose().get(node.uuid());
		var found = atRest ? null : ((AnimationComponent) holder.getAnimator()).findPose(null, Locator.of(node, rest));
		var t = (found != null && found.pose() != null ? found.pose() : rest).translation();
		return new Vec3(t.x, t.y, t.z);
	}

	/**
	 * Makes the crew's and the deck's hitboxes where they're missing and puts them where their figures and the deck are
	 * now (the deck bobs with the idle). The spear piglin wears the ghast's armour.
	 */
	private void updateParts(ServerLevel level) {
		for (var crew : Crew.values()) {
			if (!hasCrew(crew)) {
				continue;
			}
			var hitbox = hitboxes.get(crew);
			if (hitbox == null || hitbox.isRemoved()) {
				hitbox = RiftEntities.CREW_HITBOX.create(level, EntitySpawnReason.EVENT);
				if (hitbox == null) {
					continue;
				}
				hitbox.join(this, crew, crewHealth.getOrDefault(crew, CrewHitbox.maxHealth(crew)));
				place(hitbox, crew);
				hitboxes.put(crew, hitbox);
				level.addFreshEntity(hitbox);
			} else {
				place(hitbox, crew);
			}
			crewHealth.put(crew, hitbox.getHealth());
			// an invulnerable (or silent) ghast's crew are too
			hitbox.setPermanentlyInvulnerable(isPermanentlyInvulnerable());
			hitbox.setSilent(isSilent());
			if (crew == Crew.SPEAR) {
				for (var slot : ARMOUR) {
					var stack = getItemBySlot(slot);
					hitbox.wear(slot, ArmourPieces.isWorn(stack, slot) ? stack : ItemStack.EMPTY);
				}
			}
		}
		if (deck == null || deck.isRemoved()) {
			deck = RiftEntities.DECK_HITBOX.create(level, EntitySpawnReason.EVENT);
			if (deck == null) {
				return;
			}
			deck.join(this);
			placeDeck();
			level.addFreshEntity(deck);
		} else {
			placeDeck();
		}
	}

	private void place(CrewHitbox hitbox, Crew crew) {
		var spot = spot(crew);
		hitbox.setPos(spot.feet());
		hitbox.setYRot(spot.yaw());
		hitbox.setYBodyRot(spot.yaw());
		hitbox.setYHeadRot(spot.yaw());
	}

	private void placeDeck() {
		var bob = place("platform", false).subtract(place("platform", true));
		deck.setPos(toWorld(bob).add(0, -DECK_DROP, 0));
		deck.setYRot(yBodyRot);
	}

	private void discardParts() {
		hitboxes.values().forEach(Entity::discard);
		hitboxes.clear();
		if (deck != null) {
			deck.discard();
			deck = null;
		}
	}

	/** The hitboxes go with the ghast, whatever takes it (death, unloading, a command). */
	@Override
	public void onRemoval(RemovalReason reason) {
		super.onRemoval(reason);
		if (!level().isClientSide()) {
			discardParts();
		}
	}

	/**
	 * The crossbow piglin shoots a bolt every {@code crossbow_every} ticks (if 0, never) at the nearest player it can
	 * see within {@link #CROSSBOW_RANGE} blocks, who can fight (not in creative or spectator), heard loading a second
	 * before: from its right fist, as a piglin's crossbow shoots (its speed, aim and spread). The bolt is the hitbox's (its
	 * kill), and passes through the ghast, its crew and, for a rift's ghast, every rift mob (mixin/ProjectileMixin).
	 * Plays the model's {@code crossbow_shoot} animation, if it has one.
	 */
	private void crossbow(ServerLevel level) {
		int every = NetherRiftsConfig.getInstance().mobs().bombardier().crew().crossbowEvery();
		var hitbox = hitbox(Crew.CROSSBOW);
		if (every <= 0 || hitbox == null) {
			crossbowClock = 0;
			return;
		}
		crossbowClock++;
		if (crossbowClock == Math.max(1, every - CROSSBOW_LOAD_TICKS) && crossbowTarget(level, hitbox) != null) {
			level.playSound(null, hitbox.getX(), hitbox.getEyeY(), hitbox.getZ(), SoundEvents.CROSSBOW_LOADING_START.value(), SoundSource.HOSTILE, 1.0f, 1.0f);
		}
		if (crossbowClock < every) {
			return;
		}
		crossbowClock = 0;
		var target = crossbowTarget(level, hitbox);
		if (target == null) {
			return;
		}
		var from = toWorld(place(HeldItems.RIGHT, false));
		var weapon = getOffhandItem().isEmpty() ? new ItemStack(Items.CROSSBOW) : getOffhandItem().copy();
		var bolt = new Arrow(level, from.x, from.y, from.z, new ItemStack(Items.ARROW), weapon);
		bolt.setOwner(hitbox);
		bolt.pickup = AbstractArrow.Pickup.DISALLOWED;
		bolt.setSoundEvent(SoundEvents.CROSSBOW_HIT);
		// as CrossbowItem aims a mob's shot: a little above the target, more the farther it is
		double dx = target.getX() - from.x;
		double dz = target.getZ() - from.z;
		double dy = target.getY(1 / 3.0) - from.y;
		bolt.shoot(dx, dy + Math.sqrt(dx * dx + dz * dz) * 0.2, dz, 1.6f, 14 - level.getDifficulty().getId() * 4);
		level.addFreshEntity(bolt);
		level.playSound(null, from.x, from.y, from.z, SoundEvents.CROSSBOW_SHOOT, SoundSource.HOSTILE, 1.0f, 1.0f);
		holder.getAnimator().playAnimation("crossbow_shoot", 10, true);
	}

	/**
	 * The drummer on the deck drums while the ghast is in a fight (it has a target, or a player who can fight is within
	 * {@link #DRUM_RANGE} blocks), on the world's clock as every Piglin Drummer does, so they all keep time: a beat's
	 * drum, the big beat's roar, then its slam. The slam is War Beat for its piglins (a rift ghast's rift piglins, a
	 * summoned one's piglins of no rift) within reach of the deck or of the ground under the ghast, and for the crew; no
	 * Shock Beat from up here. The model's idle has the drummer's beat already.
	 */
	private void drum(ServerLevel level) {
		var drummer = hitbox(Crew.DRUMMER);
		if (drummer == null || !inFight(level)) {
			return;
		}
		int at = WarDrummer.cycleAt(level.getGameTime());
		int bigBeatStart = WarDrummer.bigBeatStart();
		int beat = NetherRiftsConfig.getInstance().mobs().drummer().beatTicks();
		double x = drummer.getX();
		double y = drummer.getY() + 1;
		double z = drummer.getZ();
		if (at < bigBeatStart && at % beat == 0) {
			WarDrummer.beatSound(level, x, y, z);
		} else if (at == bigBeatStart) {
			WarDrummer.bigBeatWindupSound(level, x, y, z);
		} else if (at == bigBeatStart + WarDrummer.BIG_BEAT_SLAM_TICK) {
			WarDrummer.slamSound(level, x, y, z);
			warBeat(level, drummer);
		}
	}

	private boolean inFight(ServerLevel level) {
		var target = getTarget();
		return target != null && target.isAlive() || level.getNearestPlayer(getX(), getY(), getZ(), DRUM_RANGE,
				player -> EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(player) && player.isAlive()) != null;
	}

	private void warBeat(ServerLevel level, CrewHitbox drummer) {
		double range = WarDrummer.buffRange();
		var ground = BurningPatches.groundBelow(level, position().add(0, -DECK_DROP, 0));
		var deck = drummer.position();
		boolean rift = RiftMobs.isRiftMob(this);
		var reach = new AABB(deck, deck).minmax(new AABB(ground, ground)).inflate(range);
		for (var piglin : level.getEntitiesOfClass(AbstractPiglin.class, reach, p -> (rift ? RiftMobs.isRiftMob(p) : !RiftMobs.isRiftMob(p) && p.isAdult())
				&& (p.distanceToSqr(deck) <= range * range || p.distanceToSqr(ground) <= range * range))) {
			WarDrummer.warBeat(piglin);
		}
		hitboxes.values().forEach(WarDrummer::warBeat);
	}

	@Nullable
	private static Player crossbowTarget(ServerLevel level, CrewHitbox hitbox) {
		return level.getNearestPlayer(hitbox.getX(), hitbox.getEyeY(), hitbox.getZ(), CROSSBOW_RANGE,
				player -> EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(player) && player.isAlive() && hitbox.hasLineOfSight(player));
	}

	/**
	 * The ghast moves as if its body reached down to its deck: floating about or pushed, it never drags the deck into
	 * the ground or a wall. (Its move control only checks the ghast's own box; the bombing run plans for the deck
	 * itself.) Stopped short like that, it stops moving that way, as a bump of its own box would, and gives up its float
	 * target for another: its move control would keep flying at a point only the ghast fits at, and an idle ghast would
	 * hover over the ground for good. Its deck stuck in blocks all the same (lifted as far as there was room), it moves as
	 * a plain ghast, so it can get clear.
	 */
	@Override
	public void move(MoverType type, Vec3 delta) {
		// with the deck stuck in blocks (no room to lift it out), it moves as a plain ghast till it's clear
		if (!noPhysics && fitsAt(position())) {
			var clamped = Entity.collideBoundingBox(this, delta, stackBox(), level(), List.of());
			if (!clamped.equals(delta)) {
				var v = getDeltaMovement();
				setDeltaMovement(clamped.x != delta.x ? 0 : v.x, clamped.y != delta.y ? 0 : v.y, clamped.z != delta.z ? 0 : v.z);
				// a bombing run sets its point again every tick, before the move control looks
				getMoveControl().setWait();
			}
			delta = clamped;
		}
		super.move(type, delta);
	}

	/**
	 * It flashes red while hurt ({@link RiftModels#hurtTint}), and a crew member's figure alone while its hitbox is. Its
	 * hitboxes follow it, and the crossbow piglin shoots. The crew are part of the ghast; riders from an old config's
	 * roster entry or a {@code /summon} with passengers would make a doubled crew, so they're sent away.
	 */
	@Override
	public void tick() {
		super.tick();
		if (level() instanceof ServerLevel level) {
			RiftModels.hurtTint(this, holder);
			tintFigures();
			if (isVehicle()) {
				getPassengers().forEach(Entity::discard);
			}
			if (isAlive()) {
				unstick();
				updateParts(level);
				// a showcase ghast's crew only stand there
				if (!Showcase.isShowcase(this)) {
					crossbow(level);
					drum(level);
				}
			}
		}
	}

	/**
	 * Each figure takes the ghast's red while the ghast is hurt, else its own hitbox's. Its armour is coloured by
	 * {@link ArmourPieces}, with the ghast. Only changes are sent.
	 */
	private void tintFigures() {
		int now = RiftModels.tint(this);
		if (now != tint) {
			// the holder has just coloured every bone
			tint = now;
			figureTint.clear();
		}
		for (var crew : Crew.values()) {
			var hitbox = hitbox(crew);
			int want = now != -1 || hitbox == null ? now : RiftModels.tint(hitbox);
			if (want == figureTint.getOrDefault(crew, now)) {
				continue;
			}
			figureTint.put(crew, want);
			for (var bone : crewBones.get(crew)) {
				if (bone instanceof ItemBone item && !bone.name().startsWith("armor_")) {
					item.updateColor(want);
				}
			}
		}
	}

	/** A war ghast always flies itself, whatever might be put on it. */
	@Nullable
	@Override
	public LivingEntity getControllingPassenger() {
		return null;
	}
}
