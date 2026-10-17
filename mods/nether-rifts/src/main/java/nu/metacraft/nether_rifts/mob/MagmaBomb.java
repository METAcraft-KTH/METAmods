package nu.metacraft.nether_rifts.mob;

import eu.pb4.polymer.core.api.entity.PolymerEntity;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.nether_rifts.NetherRiftsConfig;
import nu.metacraft.nether_rifts.raid.RiftMobs;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Dropped from a war ghast's deck by its Ghast Bombardier, or thrown by the Bombardier on foot. Clients see a thrown
 * magma cream (a snowball carrying that item).
 * On impact it blasts (damage and knockback, power 1.5) with ExplosionInteraction.NONE, so no block changes
 * and nothing is set alight, and leaves a burning patch for 2 s. The power is config ({@code mobs.bombardier.bomb_power}).
 */
public class MagmaBomb extends ThrowableItemProjectile implements PolymerEntity {

	/** Default for {@link NetherRiftsConfig.BombardierTuning#bombPower()}. */
	public static final float POWER = 1.5f;

	/** ThrowableProjectile's pull and air drag, applied in that order each tick before the move. */
	private static final double GRAVITY = 0.03;
	private static final double DRAG = 0.99f;
	/** A plain drop: let go with a little downward push. */
	private static final double DROP_SPEED = -0.15;
	private static final double MAX_LOB = 1.0;
	private static final int MAX_FLIGHT = 600;
	private static final int MAX_LOB_EXTRA = 40;

	/**
	 * Bombs are for players (and their pets and mounts). Bombing runs must not thin their own waves, so rift mobs
	 * take no blast damage or knockback; nor do things that aren't alive (loot on the ground, item frames, paintings)
	 * or armour stands.
	 */
	private static final ExplosionDamageCalculator SPARE_ALL_BUT_PLAYERS = new ExplosionDamageCalculator() {
		@Override
		public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
			return !spared(entity) && super.shouldDamageEntity(explosion, entity);
		}

		@Override
		public float getKnockbackMultiplier(Entity entity) {
			return spared(entity) ? 0f : super.getKnockbackMultiplier(entity);
		}
	};

	static boolean spared(Entity entity) {
		return !(entity instanceof LivingEntity) || entity instanceof ArmorStand || entity instanceof GhastPart || RiftMobs.isRiftMob(entity);
	}

	public MagmaBomb(EntityType<? extends MagmaBomb> type, Level level) {
		super(type, level);
	}

	public MagmaBomb(ServerLevel level, @Nullable LivingEntity thrower) {
		super(RiftEntities.MAGMA_BOMB, level);
		if (thrower != null) {
			setOwner(thrower);
		}
		setItem(new ItemStack(Items.MAGMA_CREAM));
	}

	/** Ticks until a bomb let go {@code height} blocks above the ground, moving up at {@code vy}, hits it. */
	public static int ticksToLand(double height, double vy) {
		double y = height;
		double v = vy;
		for (int tick = 1; tick < MAX_FLIGHT; tick++) {
			v = (v - GRAVITY) * DRAG;
			if (y + v <= 0) {
				return tick;
			}
			y += v;
		}
		return MAX_FLIGHT;
	}

	/** The vertical speed for a bomb let go {@code height} blocks up: a plain drop, or the gentlest lob that keeps it up {@code ticks}. */
	public static double launchSpeed(double height, int ticks) {
		for (double vy = DROP_SPEED; vy < MAX_LOB; vy += 0.025) {
			if (ticksToLand(height, vy) >= ticks) {
				return vy;
			}
		}
		return MAX_LOB;
	}

	/**
	 * The speed to let a bomb go at {@code from} so that it comes down on {@code to} after {@code ticks} ticks: an arc,
	 * with the pull and drag of each tick worked in. Over n ticks drag carries a speed of v per tick v * S(n) blocks,
	 * S(n) = sum of DRAG^k for k = 1..n; the pull takes away GRAVITY * DRAG / (1 - DRAG) * (n - S(n)) of the height.
	 */
	public static Vec3 throwSpeed(Vec3 from, Vec3 to, int ticks) {
		double carry = DRAG * (1 - Math.pow(DRAG, ticks)) / (1 - DRAG);
		double pulled = GRAVITY * DRAG / (1 - DRAG) * (ticks - carry);
		return new Vec3((to.x - from.x) / carry, (to.y - from.y + pulled) / carry, (to.z - from.z) / carry);
	}

	/**
	 * The shortest flight, at least {@code ticks} long, for a bomb lobbed from {@code from} onto {@code to} that comes no
	 * lower than {@code clearance} above {@code to} on its way down before tick {@code ticks}: someone that tall standing
	 * there is hit no sooner than the ground would be. -1 if no flight up to {@link #MAX_LOB_EXTRA} ticks longer does it.
	 */
	public static int lobTicks(Vec3 from, Vec3 to, double clearance, int ticks) {
		for (int flight = Math.max(ticks, 1); flight <= ticks + MAX_LOB_EXTRA; flight++) {
			var path = path(from, throwSpeed(from, to, flight), flight);
			double before = from.y;
			int low = flight;
			for (int tick = 1; tick <= flight; tick++) {
				double y = path.get(tick - 1).y;
				if (y < before && y <= to.y + clearance) {
					low = tick;
					break;
				}
				before = y;
			}
			if (low >= ticks) {
				return flight;
			}
		}
		return -1;
	}

	/** Where a bomb let go at {@code from} at {@code speed} is after each of its first {@code ticks} ticks in the air. */
	public static List<Vec3> path(Vec3 from, Vec3 speed, int ticks) {
		var path = new ArrayList<Vec3>(ticks);
		var at = from;
		var v = speed;
		for (int tick = 0; tick < ticks; tick++) {
			v = new Vec3(v.x * DRAG, (v.y - GRAVITY) * DRAG, v.z * DRAG);
			at = at.add(v);
			path.add(at);
		}
		return path;
	}

	@Override
	protected Item getDefaultItem() {
		return Items.MAGMA_CREAM;
	}

	@Override
	public EntityType<?> getPolymerEntityType(PacketContext context) {
		return EntityTypes.SNOWBALL;
	}

	@Override
	protected void onHit(HitResult hit) {
		super.onHit(hit);
		if (level() instanceof ServerLevel level) {
			float power = NetherRiftsConfig.getInstance().mobs().bombardier().bombPower();
			level.explode(this, null, SPARE_ALL_BUT_PLAYERS, getX(), getY(), getZ(), power, false, Level.ExplosionInteraction.NONE);
			BurningPatches.burn(level, BurningPatches.groundBelow(level, position()), getAttached(RiftMobs.RIFT));
			discard();
		}
	}
}
