package nu.metacraft.rivals.gun;

import eu.pb4.polymer.core.api.entity.PolymerEntity;
import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import it.unimi.dsi.fastutil.ints.IntList;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.rivals.PaintColor;
import nu.metacraft.rivals.Rivals;
import nu.metacraft.rivals.paint.Painter;
import org.jspecify.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Optional;

/**
 * The thrown blob. A snowball on the server (physics, hit detection) that no client ever sees:
 * {@link #sendPacketsTo} is false and a Polymer item display — a rounded, dyed blob model — rides
 * along on an {@link EntityAttachment}, squashing and stretching as it flies. Paints whatever it
 * hits; never hurts anything.
 *
 * <p>The flight has Splatoon's shape: straight and fast for {@code straight_blocks}, then a step down to
 * {@code decayed_speed} with gravity under it, and a damage that falls off with time in the air (see
 * {@link #setFlight} and {@link #damageNow}). On top of that, {@code bounces} lets a shot reflect off
 * the face it struck at the firing weapon's {@code restitution} of its speed and keep going (a
 * shooter's ball bounces once, pancaking against the face and throwing off droplets), and
 * {@code lifetime} makes a ball that has not hit anything splash the ground under itself and vanish —
 * which is what turns the same entity into the short-lived spray a bounce throws off.
 */
public final class PaintBall extends Snowball implements PolymerEntity {
	public static final EntityType<PaintBall> TYPE = EntityType.Builder.<PaintBall>of(PaintBall::new, MobCategory.MISC)
			.sized(0.25f, 0.25f)
			.clientTrackingRange(4)
			.updateInterval(10)
			.noSummon()
			.noSave()
			.build(ResourceKey.create(Registries.ENTITY_TYPE, Rivals.id("paint_ball")));

	/** How much speed a bounce keeps, by default; the live number is the weapon's {@code restitution}. */
	public static final double BOUNCE_RESTITUTION = 0.62;
	/** A lobbed arc, heavier than a vanilla snowball's 0.03; the default behind {@code gravity}. */
	public static final double GRAVITY = 0.05;
	/** How far down {@link #expire} looks for a floor to splash. */
	private static final double EXPIRE_RAY = 4.0;
	/** The blob's resting size. */
	private static final float BLOB_SCALE = 0.55f;
	/** How far the stretch goes per block per tick of speed, and the most of it a blob can wear. */
	private static final float STRETCH_PER_SPEED = 0.9f;
	private static final float STRETCH_MAX = 0.6f;
	/** The impact pancake: this much wider across the face, this much flatter along its normal. */
	private static final float IMPACT_WIDE = 1.5f;
	private static final float IMPACT_FLAT = 0.45f;
	/** Ticks the pancake is held, then ticks it eases back into the flight shape over. */
	private static final int IMPACT_HOLD = 2;
	private static final int IMPACT_BLEND = 3;
	/** Interpolation on the display: a tick in flight, two for the softer impact squash. */
	private static final int FLIGHT_INTERPOLATION = 1;
	private static final int IMPACT_INTERPOLATION = 2;
	/** Below this the velocity has no direction worth orienting to, so the blob keeps its last one. */
	private static final double ORIENT_EPSILON = 1.0e-3;
	/** Lifted off the struck face so the bounced ball does not start inside it. */
	private static final double BOUNCE_LIFT = 0.05;
	/** How much speed a landed bomb keeps off a face: enough to settle, not enough to travel. */
	private static final double BOMB_RESTITUTION = 0.3;
	/** How far above the floor a sliding curling bomb is held, so its own ray never strikes the floor. */
	private static final double CURL_LIFT = 0.05;
	/** Where the slide's floor probe starts, above the ball, and how far down it looks. */
	private static final double CURL_PROBE = 0.1;
	private static final double CURL_FLOOR_REACH = 1.5;
	/** Below this horizontal speed a slide has stopped being one, and the bomb bursts where it lies. */
	private static final double CURL_STOP = 0.02;
	/** Grains of paint thrown off the slide each tick. */
	private static final int CURL_PARTICLES = 2;
	/** Grains thrown up by a blast. */
	private static final int BLAST_PARTICLES = 24;
	/**
	 * What a bounce throws off by default: how many droplets, how long each lives, and how they leave.
	 * These are the defaults {@link WeaponTuning} is built from; what a bounce actually throws is the
	 * live {@code spatter_*} of the weapon that fired the ball.
	 */
	public static final int BOUNCE_DROPLETS = 2;
	public static final int DROPLET_LIFETIME = 8;
	public static final double DROPLET_SPEED = 0.5;
	public static final double DROPLET_SCATTER = 0.15;

	private PaintColor color = PaintColor.DATA;
	/** Which weapon's tuning this ball reads on a bounce; the shooter for a ball nobody claimed. */
	private Weapon weapon = Weapon.SHOOTER;
	private int bounces = 1;
	private int lifetime = 0;
	private int splatRadius = Painter.RADIUS;
	private float damage = Weapon.SHOOTER.damage;
	private double gravity = GRAVITY;
	private int age = 0;
	/** The straight-shot window: blocks of flight at full speed, and the speed left when it runs out. */
	private double straightBlocks = 0.0;
	private double decayedSpeed = 0.0;
	/** How far this ball has actually flown, and whether the window has closed. */
	private double travelled = 0.0;
	private boolean decayed = false;
	/** The damage falloff: full damage until {@code decayStart} ticks, then down to {@code decayedDamage}. */
	private int decayStart = 0;
	private float decayPerTick = 0.0f;
	private float decayedDamage = Weapon.SHOOTER.damage;
	/** Ticks left of a landed bomb's fuse, or −1 for a bomb that has not landed (and for every other ball). */
	private int fuse = -1;
	/**
	 * Which special this ball is, when it is one at all — read only while {@link #isBomb}, so an ordinary
	 * shot carries the splat bomb here and never looks at it. {@link Special#mode} is the whole of what
	 * this ball branches on: a fuse that counts down where it landed, a burst on contact, or a slide.
	 */
	private Special special = Special.SPLAT_BOMB;
	/** Ticks left of a curling bomb's slide, or −1 for one that is not sliding (and for everything else). */
	private int slide = -1;
	/** The bomb's blast at its edge, and how far the edge is. */
	private float edgeDamage = 0.0f;
	private double core = Weapon.SPECIAL_CORE;
	private boolean droplet = false;
	/** The display's resting size for this ball; the splat bomb is a much bigger blob. */
	private float blobScale = BLOB_SCALE;
	/** How far a landing hurts, or 0 for a ball that only hurts what it hits square on. */
	private double blast = 0.0;
	/** Ticks left of the impact squash, and the face normal it is squashed against. */
	private int impact = 0;
	private Vec3 impactNormal = new Vec3(0, 1, 0);
	private @Nullable ElementHolder blob;
	private @Nullable ItemDisplayElement blobElement;

	public PaintBall(EntityType<? extends Snowball> type, Level level) {
		super(type, level);
	}

	/** The default shot: the shooter's bounces, no lifetime. */
	public PaintBall(ServerLevel level, LivingEntity shooter, PaintColor color) {
		this(level, shooter, color, Weapon.SHOOTER_BOUNCES, 0);
	}

	/**
	 * @param shooter  who fired it, or null for a ball with no shooter (a bounce droplet whose owner has
	 *                 since gone); a null shooter leaves the position to the caller
	 * @param bounces  how many times a block hit reflects instead of ending the ball
	 * @param lifetime ticks before the ball splashes the floor under itself, or 0 for no limit
	 */
	public PaintBall(ServerLevel level, @Nullable LivingEntity shooter, PaintColor color, int bounces, int lifetime) {
		super(TYPE, level);
		this.color = color;
		this.bounces = bounces;
		this.lifetime = lifetime;
		if (shooter != null) {
			setPos(shooter.getX(), shooter.getEyeY() - 0.1, shooter.getZ());
			setOwner(shooter);
		}
		setItem(blob(color));
	}

	public static void register() {
		Registry.register(BuiltInRegistries.ENTITY_TYPE, Rivals.id("paint_ball"), TYPE);
		PolymerEntityUtils.registerType(TYPE);
	}

	public PaintColor color() {
		return color;
	}

	/**
	 * The weapon this ball came out of. Everything a ball does once it has left the barrel — how much
	 * speed a bounce keeps, what that bounce spatters — is that weapon's tuning, read at the bounce
	 * rather than copied onto the ball, so the numbers a ball in flight obeys are the current ones.
	 */
	public Weapon weapon() {
		return weapon;
	}

	public void setWeapon(Weapon weapon) {
		this.weapon = weapon;
	}

	/** How far the impact splat reaches on the struck face: 0 is a single face, 2 the slosher's 5x5. */
	public int splatRadius() {
		return splatRadius;
	}

	public void setSplatRadius(int radius) {
		this.splatRadius = radius;
	}

	/** Hearts this ball takes off someone from another team it hits square on. */
	public float damage() {
		return damage;
	}

	public void setDamage(float damage) {
		this.damage = damage;
		this.decayedDamage = damage;
	}

	/**
	 * Splatoon's shot shape, and the one thing that most separates its weapons from each other: a ball
	 * flies dead straight at its launch speed for {@code blocks}, and then drops to {@code speed} with
	 * gravity under it. {@code blocks} of 0 is a ball that falls from the moment it leaves, which is what
	 * a slosher's bucketful and a roller's flick do.
	 */
	public void setFlight(double blocks, double speed) {
		this.straightBlocks = blocks;
		this.decayedSpeed = speed;
		if (blocks > 0) setNoGravity(true);
	}

	/**
	 * The damage falloff: full {@link #damage()} until {@code start} ticks of flight, then {@code perTick}
	 * off every tick down to {@code floor}. A {@code perTick} of 0 leaves the damage flat.
	 */
	public void setDecay(int start, float perTick, float floor) {
		this.decayStart = start;
		this.decayPerTick = perTick;
		this.decayedDamage = floor;
	}

	/**
	 * What this ball is worth <em>now</em>: the launch damage until the falloff starts, then down a step
	 * a tick to its floor. Read at the hit rather than baked in at the throw, which is the whole point —
	 * a shooter's shot is worth twice as much across a corridor as it is across a courtyard.
	 */
	public float damageNow() {
		if (decayPerTick <= 0) return damage;
		return Math.max(decayedDamage, damage - decayPerTick * Math.max(0, age - decayStart));
	}

	/**
	 * Is {@code target} something this ball's colour is allowed to hurt? Only living things, and only
	 * ones not on the ball's own team: a teammate takes the paint under their feet and nothing else. No
	 * team at all — a mob, a player who never ran {@code /rivals setup} — counts as fair game, because
	 * the alternative is a mob that the whole arena can hide behind.
	 */
	public static boolean hostile(PaintColor color, Entity target) {
		if (!(target instanceof LivingEntity)) return false;
		Optional<PaintColor> theirs = PaintColor.byTeam(target.getTeam());
		return theirs.isEmpty() || theirs.get() != color;
	}

	/**
	 * Override the fall rate for this ball. Read back through {@link #getDefaultGravity} every tick, so it
	 * can be set at any point in the ball's life; the slosher sets it once, before the throw.
	 */
	public void setGravity(double gravity) {
		this.gravity = gravity;
	}

	/** Ticks this ball lives before it splashes the floor under itself, or 0 for no limit. */
	public int lifetime() {
		return lifetime;
	}

	/** Bounces this ball has left; 0 means the next block hit ends it. */
	public int bouncesLeft() {
		return bounces;
	}

	/**
	 * Whether this ball is spray thrown off someone else's bounce. Droplets never throw droplets of their
	 * own: a droplet that spawned droplets would be a chain with no end to it.
	 */
	public boolean isDroplet() {
		return droplet;
	}

	public void setDroplet(boolean droplet) {
		this.droplet = droplet;
	}

	/**
	 * How big the blob display is at rest. The flight stretch is computed from it, so a bigger ball is
	 * bigger in every shape it takes.
	 */
	public void setBlobScale(float scale) {
		this.blobScale = scale;
	}

	public float blobScale() {
		return blobScale;
	}

	/**
	 * Make this ball a splat bomb: anything within {@code range} blocks of where it lands takes its
	 * {@link #damage()}, and the landing paints its splat radius wherever it happened rather than only
	 * on the face a direct hit found. A range of 0 leaves it an ordinary ball.
	 */
	public void setBlast(double range) {
		this.blast = range;
	}

	public double blast() {
		return blast;
	}

	public boolean isBomb() {
		return blast > 0;
	}

	/**
	 * Which special this ball is. Only read while {@link #isBomb}; {@link PaintWeapon#special} sets it
	 * along with the numbers off that special's {@link SpecialTuning}, and the ball comes back to the
	 * sheet at the landing for the ones a landing needs — the fuse, the slide — so a bomb in the air
	 * obeys today's numbers rather than the ones its thrower's tank was full of.
	 */
	public Special special() {
		return special;
	}

	public void setSpecial(Special special) {
		this.special = special;
	}

	/** Ticks left of a curling bomb's slide, or −1 when it is not sliding. For the tests. */
	public int slide() {
		return slide;
	}

	/** The holder carrying the blob display, or null before the first tick and after removal. */
	public @Nullable ElementHolder blobHolder() {
		return blob;
	}

	/**
	 * The item the entity carries. Clients never see it — {@link #sendPacketsTo} is false, so the entity
	 * and the item-break event vanilla would fire on impact never reach anyone — but the stack is still
	 * what the ball reads as anywhere it is inspected server-side, and it carries the colour.
	 */
	public static ItemStack blob(PaintColor color) {
		ItemStack stack = new ItemStack(Items.FIREWORK_STAR);
		stack.set(DataComponents.FIREWORK_EXPLOSION,
				new FireworkExplosion(FireworkExplosion.Shape.SMALL_BALL, IntList.of(color.rgb), IntList.of(), false, false));
		return stack;
	}

	@Override
	protected Item getDefaultItem() {
		return Items.FIREWORK_STAR;
	}

	@Override
	public EntityType<?> getPolymerEntityType(PacketContext context) {
		return EntityTypes.SNOWBALL;
	}

	/** The entity itself is never sent: the blob display is the whole of what players see. */
	@Override
	public boolean sendPacketsTo(ServerPlayer player) {
		return false;
	}

	@Override
	protected double getDefaultGravity() {
		return gravity;
	}

	@Override
	public void tick() {
		Vec3 was = position();
		super.tick();
		if (isRemoved() || !(level() instanceof ServerLevel serverLevel)) return;
		if (blob == null) attachBlob();
		travelled += position().distanceTo(was);
		// The end of the straight shot: the ball keeps its direction, drops to the decayed speed and
		// starts falling. One step rather than a taper, which is what Splatoon does and what makes the
		// edge of a weapon's range a place rather than a gradient.
		if (!decayed && straightBlocks > 0 && travelled >= straightBlocks) {
			decayed = true;
			setNoGravity(false);
			Vec3 v = getDeltaMovement();
			if (v.lengthSqr() > 1.0e-9) setDeltaMovement(v.normalize().scale(decayedSpeed));
		}
		shape();
		age++;
		// A landed bomb counts down where it lies, so it can be run away from. Its own lifetime still
		// stands behind that, for one that never finds a floor.
		if (fuse > 0 && --fuse == 0) {
			detonate(serverLevel, position());
			return;
		}
		if (slide >= 0 && !slideOn(serverLevel)) return;
		// A slide that has started governs its own end: a curling bomb thrown down a long corridor must
		// not be cut short by the lifetime, which is there for one that never finds a floor at all.
		if (lifetime > 0 && age >= lifetime && slide < 0) expire(serverLevel);
	}

	/**
	 * The blob on a stack: the rounded model the pack ships, tinted. What the flying display wears, and
	 * what {@link SpecialDialog} draws a special's picture with — there is no item a special <em>is</em>,
	 * so the blob it flies as is the truest picture of it there can be. A null colour leaves it untinted,
	 * which is what a viewer on no team sees.
	 */
	public static ItemStack blobModel(@Nullable PaintColor color) {
		return blobModel(color, null);
	}

	/**
	 * The same, as the bomb {@code special} flies as — {@code splat_bomb}, {@code burst_bomb} or
	 * {@code curling_bomb}, three shapes a player can tell apart in the air: the fat blob with a wick that
	 * waits, the small spiked one that goes off on touch, the flat puck that slides. Null is a plain shot.
	 */
	public static ItemStack blobModel(@Nullable PaintColor color, @Nullable Special special) {
		ItemStack stack = new ItemStack(Items.STICK);
		stack.set(DataComponents.ITEM_MODEL, Rivals.id(special == null ? "blob" : special.id));
		if (color != null) stack.set(DataComponents.DYED_COLOR, new DyedItemColor(color.rgb));
		return stack;
	}

	/** The display that players actually see: a dyed blob model glued to this entity, gliding a tick behind. */
	private void attachBlob() {
		// Lazily, on the first tick, which is after special() has said what this ball is.
		ItemStack stack = blobModel(color, isBomb() ? special : null);
		ElementHolder holder = new ElementHolder();
		ItemDisplayElement element = new ItemDisplayElement(stack);
		element.setItemDisplayContext(ItemDisplayContext.FIXED);
		element.setInterpolationDuration(FLIGHT_INTERPOLATION);
		element.setTeleportDuration(1);
		element.setScale(new Vector3f(blobScale, blobScale, blobScale));
		holder.addElement(element);
		EntityAttachment.ofTicking(holder, this);
		blob = holder;
		blobElement = element;
	}

	/**
	 * Squash and stretch, the thing that separates a blob of paint from a pebble. In flight the model's
	 * local +Y is turned to point along the velocity and the blob is drawn out along it by the speed —
	 * what it gains in length it loses across, so the volume reads constant. On a bounce it pancakes
	 * against the face it struck for {@link #IMPACT_HOLD} ticks and then eases back into the flight
	 * shape over {@link #IMPACT_BLEND} more; a ball that simply kept its flying shape through a bounce
	 * read as bouncing off nothing.
	 *
	 * <p>The old vertical sine wobble is gone: it was a shape unrelated to what the ball was doing, and
	 * at the tick rate a display interpolates over it mostly read as jitter.
	 */
	private void shape() {
		if (blobElement == null) return;
		Vec3 v = getDeltaMovement();
		float stretch = (float) Math.min(STRETCH_MAX, STRETCH_PER_SPEED * v.length());
		Vector3f flight = new Vector3f(blobScale / (1 + stretch), blobScale * (1 + stretch), blobScale / (1 + stretch));
		if (impact > 0) {
			boolean held = impact > IMPACT_BLEND;
			float eased = held ? 0.0f : (IMPACT_BLEND - impact) / (float) IMPACT_BLEND;
			Vector3f pancake = new Vector3f(blobScale * IMPACT_WIDE, blobScale * IMPACT_FLAT, blobScale * IMPACT_WIDE);
			blobElement.setScale(pancake.lerp(flight, eased));
			blobElement.setLeftRotation(orient(impactNormal));
			blobElement.setInterpolationDuration(held ? IMPACT_INTERPOLATION : FLIGHT_INTERPOLATION);
			impact--;
		} else {
			blobElement.setScale(flight);
			blobElement.setLeftRotation(orient(v));
			blobElement.setInterpolationDuration(FLIGHT_INTERPOLATION);
		}
		blobElement.startInterpolationIfDirty();
	}

	/** The rotation that turns the model's local up onto {@code direction}; identity if there is none. */
	private static Quaternionf orient(Vec3 direction) {
		double length = direction.length();
		if (length < ORIENT_EPSILON) return new Quaternionf();
		return new Quaternionf().rotationTo(0.0f, 1.0f, 0.0f,
				(float) (direction.x / length), (float) (direction.y / length), (float) (direction.z / length));
	}

	/** Out of time: splash the first surface within {@link #EXPIRE_RAY} straight down, then go. */
	private void expire(ServerLevel level) {
		if (isBomb()) {
			detonate(level, position());
			return;
		}
		Vec3 from = position();
		Vec3 to = from.subtract(0, EXPIRE_RAY, 0);
		BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
		if (hit.getType() == HitResult.Type.BLOCK) {
			Painter.splash(level, hit.getLocation(), hit.getBlockPos(), hit.getDirection(), color, random, splatRadius, this);
		}
		discard();
	}

	/** The bomb's blast at its edge, and how far in from the centre the full damage reaches. */
	public void setBlast(double range, float edgeDamage, double core) {
		setBlast(range);
		this.edgeDamage = edgeDamage;
		this.core = core;
	}

	/** How far this ball has actually flown, in blocks. What the straight-shot window is measured against. */
	public double travelled() {
		return travelled;
	}

	/** Ticks left of a landed bomb's fuse, or −1 for one that has not landed. For the tests. */
	public int fuse() {
		return fuse;
	}

	/** Start the countdown, if it has not already started. A bomb lands once. */
	private void land(int ticks) {
		if (fuse < 0) fuse = Math.max(1, ticks);
	}

	/**
	 * A curling bomb meeting a block: the floor is what it was thrown to find, and a wall is what it
	 * comes off. Landing turns the throw into a slide — gravity off, the fall thrown away, the horizontal
	 * speed kept — and a wall reflects that speed across the struck face, so a bomb down a corridor
	 * carries on painting instead of stopping in a corner. A ceiling hit before the slide has started
	 * reflects in full, because that one is still a throw.
	 */
	private void curl(ServerLevel level, BlockHitResult hit) {
		Vec3 v = getDeltaMovement();
		if (hit.getDirection() == Direction.UP) {
			setPos(hit.getLocation().add(0, CURL_LIFT, 0));
			setNoGravity(true);
			setDeltaMovement(v.x, 0.0, v.z);
			if (slide < 0) slide = Math.max(1, SpecialTuning.get(special).intValue(SpecialTuning.Param.SLIDE_TICKS));
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.SLIME_BLOCK_STEP, SoundSource.PLAYERS, 0.7f, 1.4f);
			return;
		}
		Vec3 normal = Vec3.atLowerCornerOf(hit.getDirection().getUnitVec3i());
		Vec3 reflected = v.subtract(normal.scale(2 * v.dot(normal)));
		setDeltaMovement(slide >= 0 ? new Vec3(reflected.x, 0.0, reflected.z) : reflected);
		setPos(hit.getLocation().add(normal.scale(BOUNCE_LIFT)));
		impact = IMPACT_HOLD + IMPACT_BLEND;
		impactNormal = normal;
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.SLIME_BLOCK_STEP, SoundSource.PLAYERS, 0.6f, 1.1f);
	}

	/**
	 * One tick of the slide: the cell under the bomb painted, the bomb set back down on whatever floor
	 * that ray found, and its speed taken down by {@code friction}. The floor is found by a short ray
	 * straight down rather than assumed to be the block below — the same thing the roller's roll does —
	 * so a curling bomb goes down a stair and over a slab leaving its line on the surface it actually
	 * crossed. Off the end of a floor it stops sliding and falls, and lands again wherever it lands.
	 *
	 * <p>Returns whether the ball is still there, so the tick loop can stop at a bomb that just burst.
	 */
	private boolean slideOn(ServerLevel level) {
		Vec3 from = position().add(0, CURL_PROBE, 0);
		BlockHitResult down = level.clip(new ClipContext(from, from.subtract(0, CURL_FLOOR_REACH, 0),
				ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
		if (down.getType() != HitResult.Type.BLOCK) {
			slide = -1;
			setNoGravity(false);
			return true;
		}
		Painter.paintFace(level, down.getBlockPos(), down.getDirection(), color, Painter.ownerOf(this));
		Painter.burst(level, Painter.crumbs(color), position(), CURL_PARTICLES, 0.15, 0.05, 0.15, 0.02);
		setPos(getX(), down.getLocation().y + CURL_LIFT, getZ());
		Vec3 v = getDeltaMovement();
		double friction = SpecialTuning.get(special).value(SpecialTuning.Param.FRICTION);
		setDeltaMovement(v.x * friction, 0.0, v.z * friction);
		// Out of slide, or down to a crawl: a bomb that has stopped moving has finished sliding, and
		// waiting for the taper to reach zero would leave it sitting there for nothing.
		if (--slide <= 0 || v.horizontalDistanceSqr() < CURL_STOP * CURL_STOP) {
			detonate(level, position());
			return false;
		}
		return true;
	}

	/**
	 * The splat bomb going off: the paint where it lies, the bang, and a hit on everyone from another
	 * team in the blast. Called when the fuse runs out and when the bomb's own lifetime does, because a
	 * bomb that never finds a floor should still go off rather than vanish.
	 */
	private void detonate(ServerLevel level, Vec3 at) {
		BlockHitResult down = level.clip(new ClipContext(at, at.subtract(0, EXPIRE_RAY, 0),
				ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
		if (down.getType() == HitResult.Type.BLOCK) {
			Painter.splash(level, down.getLocation(), down.getBlockPos(), down.getDirection(), color, random, splatRadius, this);
		}
		blast(level, at);
	}

	/**
	 * The same, for a bomb that went off <em>on</em> a face rather than over a floor: the paint goes on
	 * what it struck. That is what a burst bomb thrown at a wall has to do — the floor under the wall is
	 * not where the player aimed — and it is the one thing the downward clip cannot say.
	 */
	private void detonateOn(ServerLevel level, BlockHitResult hit) {
		Painter.splash(level, hit.getLocation(), hit.getBlockPos(), hit.getDirection(), color, random, splatRadius, this);
		blast(level, hit.getLocation());
	}

	/** The bang: the blast, the grains it throws up, and the end of the ball. */
	private void blast(ServerLevel level, Vec3 at) {
		hurtNearby(level, at);
		Painter.burst(level, Painter.crumbs(color), at, BLAST_PARTICLES, 0.4, 0.3, 0.4, 0.08);
		discard();
	}

	/**
	 * Everyone from another team within {@link #blast} blocks takes the bomb, falling off from
	 * {@link #damage} at the centre to {@link #edgeDamage} at the edge, linear in distance <em>squared</em>
	 * — which is the shape Splatcraft's {@code InkExplosion} uses, and reads as a blast that is lethal
	 * where it lands and a shove where it does not. Inside {@link #core} blocks it is at full: the falloff
	 * starts at the edge of the bomb rather than at a point.
	 *
	 * <p>No line-of-sight clip. One per victim is a real cost, and in a world of stairs and slabs it is
	 * wrong about as often as it is right — a bomb at your feet behind a half-step would do nothing.
	 */
	private void hurtNearby(ServerLevel level, Vec3 at) {
		level.playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_BLOCK_BREAK, SoundSource.PLAYERS, 1.2f, 0.7f);
		if (damage <= 0 || blast <= 0) return;
		double inner = Math.min(core, blast);
		for (LivingEntity caught : level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(at, blast * 2, blast * 2, blast * 2))) {
			if (!hostile(color, caught)) continue;
			double distanceSqr = caught.position().distanceToSqr(at);
			if (distanceSqr > blast * blast) continue;
			float hurt = damage;
			if (distanceSqr > inner * inner) {
				double span = blast * blast - inner * inner;
				double t = span <= 0 ? 1.0 : (distanceSqr - inner * inner) / span;
				hurt = (float) (damage + (edgeDamage - damage) * t);
			}
			if (hurt > 0 && PaintDamage.hurt(level, caught, level.damageSources().thrown(this, getOwner()), hurt)) {
				InkOnScreen.hit(caught, color, hurt);
			}
		}
	}

	/**
	 * {@link Snowball#onHit} discards the ball once the hit has been handled, so a bounce cannot go
	 * through the usual {@link #onHitBlock} path: it has to answer the hit here and return before super
	 * ever runs. Everything else — entity hits, a world-border hit (bouncing off the border would leave
	 * the ball skimming a wall that is not there), and the block hit that spends the last bounce — falls
	 * through to v2's behaviour (super dispatches to {@code onHitBlock}/{@code onHitEntity}, then discards).
	 */
	@Override
	protected void onHit(HitResult result) {
		// What a bomb does with a hit is the whole of what separates the three specials, and it is this
		// one branch: a burst bomb is gone on contact, a curling bomb takes the hit as a floor to slide
		// along or a wall to come off, and a splat bomb — Splatoon's, and this module's since round 7 —
		// is not a contact grenade at all. It bounces where it is thrown and counts down on the ground,
		// which is what makes it a thing you can run away from, and what makes throwing one at someone's
		// feet a decision about where they will be in a second rather than about where they are. Hitting
		// a player does not arm that one either; it rolls off them.
		if (isBomb() && level() instanceof ServerLevel bombLevel) {
			if (special.mode == Special.Mode.IMPACT) {
				if (result instanceof BlockHitResult hit && !hit.isWorldBorderHit()) {
					super.onHitBlock(hit);
					detonateOn(bombLevel, hit);
					return;
				}
				if (result instanceof EntityHitResult) {
					// On the body: the paint goes on the floor under it, the blast catches whoever else is
					// standing there, and the one who was hit is inside the core of it.
					detonate(bombLevel, position());
					return;
				}
				super.onHit(result);
				return;
			}
			if (special.mode == Special.Mode.CURL) {
				if (result instanceof BlockHitResult hit && !hit.isWorldBorderHit()) {
					super.onHitBlock(hit);
					curl(bombLevel, hit);
					return;
				}
				if (result instanceof EntityHitResult) return; // it slides past feet; the end of the slide decides
				super.onHit(result);
				return;
			}
			if (result instanceof BlockHitResult hit && !hit.isWorldBorderHit()) {
				super.onHitBlock(hit);
				land(SpecialTuning.get(special).intValue(SpecialTuning.Param.FUSE));
				Vec3 normal = Vec3.atLowerCornerOf(hit.getDirection().getUnitVec3i());
				Vec3 v = getDeltaMovement();
				setDeltaMovement(v.subtract(normal.scale(2 * v.dot(normal))).scale(BOMB_RESTITUTION));
				setPos(hit.getLocation().add(normal.scale(BOUNCE_LIFT)));
				bombLevel.playSound(null, getX(), getY(), getZ(), SoundEvents.SLIME_BLOCK_STEP, SoundSource.PLAYERS, 0.7f, 1.2f);
				return;
			}
			if (result instanceof EntityHitResult) return; // it glances off; the fuse decides, not the touch
			super.onHit(result);
			return;
		}
		if (bounces > 0 && result instanceof BlockHitResult hit && !hit.isWorldBorderHit()
				&& level() instanceof ServerLevel serverLevel) {
			super.onHitBlock(hit); // the vanilla block-hit effects still belong to a bounce
			Painter.splash(serverLevel, hit.getLocation(), hit.getBlockPos(), hit.getDirection(), color, random, splatRadius, this);
			bounces--;
			Vec3 normal = Vec3.atLowerCornerOf(hit.getDirection().getUnitVec3i());
			Vec3 v = getDeltaMovement();
			Vec3 reflected = v.subtract(normal.scale(2 * v.dot(normal)))
					.scale(WeaponTuning.get(weapon).value(WeaponTuning.Param.RESTITUTION));
			setDeltaMovement(reflected);
			setPos(hit.getLocation().add(normal.scale(BOUNCE_LIFT)));
			impact = IMPACT_HOLD + IMPACT_BLEND;
			impactNormal = normal;
			spatter(serverLevel, hit.getLocation(), reflected);
			return;
		}
		super.onHit(result);
	}

	/**
	 * What a bounce throws off: a couple of droplets that leave along the reflection, at half its speed
	 * and scattered a little, each a short-lived single-face ball of its own. Paint that keeps going
	 * after the blob has left is most of what makes the bounce read as liquid rather than as rubber. The
	 * droplets carry the flag, so nothing they hit throws more.
	 */
	private void spatter(ServerLevel level, Vec3 at, Vec3 reflected) {
		level.playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_BLOCK_STEP, SoundSource.BLOCKS, 0.5f, 1.6f);
		if (droplet) return;
		WeaponTuning tuning = WeaponTuning.get(weapon);
		LivingEntity shooter = getOwner() instanceof LivingEntity living ? living : null;
		int count = tuning.intValue(WeaponTuning.Param.SPATTER_COUNT);
		double scatterBy = tuning.value(WeaponTuning.Param.SPATTER_SCATTER);
		double speed = tuning.value(WeaponTuning.Param.SPATTER_SPEED);
		for (int i = 0; i < count; i++) {
			PaintBall drop = new PaintBall(level, shooter, color, 0, tuning.intValue(WeaponTuning.Param.SPATTER_LIFETIME));
			drop.setWeapon(weapon);
			drop.setDroplet(true);
			drop.setSplatRadius(0);
			drop.setDamage(tuning.floatValue(WeaponTuning.Param.SPATTER_DAMAGE));
			drop.setPos(at.x, at.y, at.z);
			Vec3 scatter = new Vec3(random.nextDouble() * 2 - 1, random.nextDouble() * 2 - 1, random.nextDouble() * 2 - 1);
			if (scatter.lengthSqr() > 1.0e-6) scatter = scatter.normalize().scale(scatterBy);
			drop.setDeltaMovement(reflected.scale(speed).add(scatter));
			level.addFreshEntity(drop);
		}
	}

	@Override
	protected void onHitBlock(BlockHitResult hit) {
		super.onHitBlock(hit);
		if (level() instanceof ServerLevel serverLevel) {
			Painter.splash(serverLevel, hit.getLocation(), hit.getBlockPos(), hit.getDirection(), color, random, splatRadius, this);
		}
	}

	/**
	 * A direct hit. The paint goes on the ground under the target the same way it always has — that is
	 * what makes a hit read as a hit — and on top of that the shot now hurts, but only someone from
	 * another team. Vanilla's snowball damage is deliberately not used (it only ever hurt blazes); this
	 * is the weapon's own number, attributed to the shooter so a kill goes on their name.
	 *
	 * <p>Squids are ordinary players here and take it like anyone else: squid form is cover, not
	 * armour. Spectators and creative players are refused by vanilla inside {@code hurtServer}.
	 */
	@Override
	public void onHitEntity(EntityHitResult hit) {
		if (!(level() instanceof ServerLevel serverLevel)) return;
		BlockPos below = hit.getEntity().blockPosition().below();
		Painter.splash(serverLevel, position(), below, Direction.UP, color, random, splatRadius, this);
		// What it is worth now, not what it left the barrel worth: see damageNow(). Through PaintDamage,
		// because a weapon that lands two of these on one tick must land both of them.
		float hurt = damageNow();
		if (hurt > 0 && hostile(color, hit.getEntity())
				&& PaintDamage.hurt(serverLevel, hit.getEntity(), serverLevel.damageSources().thrown(this, getOwner()), hurt)) {
			// And a faceful of it on the way past: the shooter's colour, on the victim's screen.
			InkOnScreen.hit(hit.getEntity(), color, hurt);
		}
	}

	/**
	 * The blob is not an entity of its own: nothing else would ever take it down. Hooked on
	 * {@code onRemoval} rather than {@code remove}, because {@link #setRemoved} is final and calls
	 * {@code onRemoval} directly — a chunk unload takes that path and never goes through {@code remove}.
	 */
	@Override
	public void onRemoval(RemovalReason reason) {
		super.onRemoval(reason);
		if (blob != null) {
			blob.destroy();
			blob = null;
			blobElement = null;
		}
	}
}
