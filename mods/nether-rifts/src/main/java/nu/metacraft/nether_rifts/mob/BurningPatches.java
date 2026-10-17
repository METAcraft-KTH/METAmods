package nu.metacraft.nether_rifts.mob;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ChunkAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Brightness;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import nu.metacraft.nether_rifts.NetherRifts;
import nu.metacraft.nether_rifts.NetherRiftsConfig;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * The ground under a falling magma bomb: a warning where it will land, then a 2 s burning patch where it did (lava
 * particles; players standing in it catch fire). The warning shows {@code warn_ticks} (1.5 s) ahead, as wide as the
 * blast reaches: a red ring lying on the ground, full bright so it reads from afar and in the dark, with the bomb's
 * shadow darkening in it as the bomb falls ({@link Marker}; {@code warn_shadow}), embers round its edge that show
 * whatever a player's particle setting, and a whistle. Neither touches blocks. Each belongs to the rift whose bomb it
 * is (if any), and goes when that raid ends. The numbers are the defaults of the config's {@code mobs.bombardier}
 * section.
 */
public final class BurningPatches {

	/** Defaults for {@link NetherRiftsConfig.BombardierTuning}. */
	public static final int WARN_TICKS = 30;
	public static final int BURN_TICKS = 40;
	public static final int PLAYER_FIRE_TICKS = 60;
	public static final double RADIUS = 1.5;

	/** The ring lies this far over the ground, clear of it. */
	private static final float LIFT = 0.02f;
	/** The shadow's darkness as the warning goes up, and as the bomb lands (vanilla's mobs cast 1). */
	public static final float SHADOW_FROM = 0.3f;
	public static final float SHADOW_TO = 1.6f;
	/** Embers round the warning's edge every this many ticks, about one a block. */
	private static final int EMBERS_EVERY = 3;

	/** The ring's look: paper drawn with the ring's item model (a flat face-up square of the ring sprite). */
	private static final ItemStack RING = ring();

	/** A warning's ring and shadow, while it shows: one display entity, and the tick it went up. */
	public record Marker(ElementHolder holder, ItemDisplayElement ring, long from) {}

	public record Patch(Vec3 at, long endsAt, boolean warning, @Nullable UUID rift, @Nullable Marker marker) {}

	private static final Map<ServerLevel, List<Patch>> PATCHES = new WeakHashMap<>();

	private BurningPatches() {}

	public static void init() {
		ServerTickEvents.END_LEVEL_TICK.register(BurningPatches::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			PATCHES.values().forEach(patches -> patches.forEach(BurningPatches::takeDown));
			PATCHES.clear();
		});
	}

	public static void warn(ServerLevel level, Vec3 at, @Nullable UUID rift) {
		var tuning = tuning();
		var marker = tuning.warning().shadow() ? marker(level, at, (float) tuning.warnRadius()) : null;
		add(level, new Patch(at, level.getGameTime() + tuning.warnTicks(), true, rift, marker));
		level.playSound(null, at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.HOSTILE, 1.0f, 0.5f);
	}

	public static void burn(ServerLevel level, Vec3 at, @Nullable UUID rift) {
		add(level, new Patch(at, level.getGameTime() + tuning().burnTicks(), false, rift, null));
	}

	/** Drops the rift's warnings and patches: its raid is over, and its bombs are gone with it. */
	public static void clear(ServerLevel level, UUID rift) {
		var patches = PATCHES.get(level);
		if (patches != null) {
			patches.removeIf(p -> {
				if (rift.equals(p.rift())) {
					takeDown(p);
					return true;
				}
				return false;
			});
		}
	}

	/** Where something falling straight down from {@code from} lands: the first solid surface below, roofs and overhangs included. */
	public static Vec3 groundBelow(ServerLevel level, Vec3 from) {
		var hit = level.clip(new ClipContext(from, from.add(0, -64, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
		return hit.getType() == HitResult.Type.MISS ? from : hit.getLocation();
	}

	public static List<Patch> active(ServerLevel level) {
		return List.copyOf(PATCHES.getOrDefault(level, List.of()));
	}

	private static NetherRiftsConfig.BombardierTuning tuning() {
		return NetherRiftsConfig.getInstance().mobs().bombardier();
	}

	private static void add(ServerLevel level, Patch patch) {
		PATCHES.computeIfAbsent(level, l -> new ArrayList<>()).add(patch);
	}

	private static ItemStack ring() {
		var stack = new ItemStack(Items.PAPER);
		stack.set(DataComponents.ITEM_MODEL, NetherRifts.getID("bomb_warning"));
		return stack;
	}

	/**
	 * The warning's ring and the bomb's shadow, in one item display lying on the ground: the ring sprite scaled to the
	 * warning's width, full bright, and a vanilla entity shadow as wide, faint at first. A tick later (once clients have
	 * it) the shadow starts darkening, eased by the clients themselves till the bomb lands ({@link #tick}).
	 */
	private static Marker marker(ServerLevel level, Vec3 at, float radius) {
		var ring = new ItemDisplayElement(RING.copy());
		ring.setItemDisplayContext(ItemDisplayContext.NONE);
		ring.setBrightness(new Brightness(15, 15));
		ring.setScale(new Vector3f(radius * 2, 1, radius * 2));
		ring.setShadowRadius(radius);
		ring.setShadowStrength(SHADOW_FROM);
		var holder = new ElementHolder();
		holder.addElement(ring);
		ChunkAttachment.ofTicking(holder, level, at.add(0, LIFT, 0));
		return new Marker(holder, ring, level.getGameTime());
	}

	private static void takeDown(Patch patch) {
		if (patch.marker() != null) {
			patch.marker().holder().destroy();
		}
	}

	private static void tick(ServerLevel level) {
		var patches = PATCHES.get(level);
		if (patches == null || patches.isEmpty()) {
			return;
		}
		long now = level.getGameTime();
		patches.removeIf(p -> {
			if (p.endsAt() <= now) {
				takeDown(p);
				return true;
			}
			return false;
		});
		var tuning = tuning();
		double radius = tuning.patchRadius();
		double warnRadius = tuning.warnRadius();
		for (var p : patches) {
			if (p.warning()) {
				var marker = p.marker();
				if (marker != null && now == marker.from() + 1) {
					marker.ring().setInterpolationDuration((int) (p.endsAt() - now));
					marker.ring().setShadowStrength(SHADOW_TO);
					marker.ring().startInterpolation();
				}
				if (now % EMBERS_EVERY == 0) {
					embers(level, p.at(), warnRadius, now);
				}
				continue;
			}
			level.sendParticles(ParticleTypes.LAVA, p.at().x, p.at().y + 0.1, p.at().z, 2, radius / 2, 0.05, radius / 2, 0);
			var box = new AABB(p.at().x - radius, p.at().y, p.at().z - radius, p.at().x + radius, p.at().y + 2.0, p.at().z + radius);
			for (var player : level.getEntitiesOfClass(Player.class, box, pl -> !pl.isSpectator() && !pl.isCreative())) {
				player.setRemainingFireTicks(Math.max(player.getRemainingFireTicks(), tuning.playerFireTicks()));
			}
		}
	}

	/**
	 * Flames round the warning's edge, about one a block, turning a little each time; always shown, as a player's
	 * particle setting would leave out most of them.
	 */
	private static void embers(ServerLevel level, Vec3 at, double radius, long now) {
		int count = Math.max(8, Mth.ceil(Mth.TWO_PI * radius));
		float turn = (now / EMBERS_EVERY) % 2 * (Mth.TWO_PI / count / 2);
		for (int i = 0; i < count; i++) {
			float angle = turn + i * Mth.TWO_PI / count;
			level.sendParticles(ParticleTypes.FLAME, false, true, at.x + Mth.cos(angle) * radius, at.y + 0.1,
					at.z + Mth.sin(angle) * radius, 1, 0, 0.02, 0, 0);
		}
	}
}
