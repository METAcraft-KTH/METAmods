package nu.metacraft.rivals.paint;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import nu.metacraft.rivals.Arena;
import nu.metacraft.rivals.PaintColor;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Where a hit puts paint. The struck block is the surface; a full face takes a paint block in the cell
 * in front, whose face flag points back at the surface. Any other shape (stairs, slabs, fences, walls)
 * takes {@link PaintDisplays} quads instead, wrapped around the block's own collision boxes. A splat
 * covers the 3×3 of surface blocks around the hit in the plane of the face, corners dropped at random.
 */
public final class Painter {
	public static final int RADIUS = 1;
	private static final Direction[] DIRECTIONS = Direction.values();

	private Painter() {}

	/**
	 * Paint a blob around {@code struck}'s {@code face}. Returns how many cells changed; a recolour counts
	 * once however many faces it flips.
	 */
	public static int splat(ServerLevel level, BlockPos struck, Direction face, PaintColor color, RandomSource random) {
		return splat(level, struck, face, color, random, RADIUS);
	}

	/**
	 * The same, over a square of side {@code 2 * radius + 1}: radius 0 paints only the struck face (a
	 * bounce's droplet), radius 2 the slosher's and the roller's flick's 5x5. The ragged corners are what
	 * keeps a blob from reading as a square, so there are none to drop at radius 0.
	 */
	public static int splat(ServerLevel level, BlockPos struck, Direction face, PaintColor color, RandomSource random, int radius) {
		return splat(level, struck, face, color, random, radius, (UUID) null);
	}

	/** The same, crediting {@code painter} with every face it covers. */
	public static int splat(ServerLevel level, BlockPos struck, Direction face, PaintColor color,
			RandomSource random, int radius, @Nullable UUID painter) {
		int painted = 0;
		for (int a = -radius; a <= radius; a++) {
			for (int b = -radius; b <= radius; b++) {
				boolean corner = radius > 0 && Math.abs(a) == radius && Math.abs(b) == radius;
				if (corner && random.nextBoolean()) continue;
				if (paintFace(level, offsetInPlane(struck, face.getAxis(), a, b), face, color, painter)) painted++;
			}
		}
		return painted;
	}

	/** Offset {@code origin} by (a, b) within the plane perpendicular to {@code normal}. */
	static BlockPos offsetInPlane(BlockPos origin, Direction.Axis normal, int a, int b) {
		return switch (normal) {
			case Y -> origin.offset(a, 0, b);
			case X -> origin.offset(0, a, b);
			case Z -> origin.offset(a, b, 0);
		};
	}

	/**
	 * Who to credit for paint thrown by {@code source}: the player themselves for a roller or a charger's
	 * line, and the shooter for anything they fired, since a paint ball is the shot and not the shooter.
	 * Null for paint with nobody behind it — a test, or a droplet whose shooter has since logged out.
	 */
	public static @Nullable UUID ownerOf(@Nullable Entity source) {
		if (source instanceof Player player) return player.getUUID();
		if (source instanceof Projectile shot && shot.getOwner() instanceof Player player) return player.getUUID();
		return null;
	}

	/** Either paint block. */
	public static boolean isPaint(BlockState state) {
		return state.getBlock() instanceof Paint;
	}

	/**
	 * Anything solid: not air, not replaceable (grass, snow), not a liquid, not paint, and not one of the
	 * shapes ink falls straight through ({@link Unpaintable} — the grates, bars and panes). Waterlogged
	 * blocks hold paint like any other — the test is the block, not the fluid state it carries.
	 */
	public static boolean paintable(BlockState surface) {
		return !surface.isAir() && !surface.canBeReplaced() && !(surface.getBlock() instanceof LiquidBlock)
				&& !isPaint(surface) && !Unpaintable.test(surface);
	}

	/**
	 * Paint one face (spec §4): the {@code face} side of the block at {@code surface}. A face that is not
	 * full takes display quads. Otherwise the cell in front must be air or paint. Air, or another colour,
	 * becomes a connected cell for this face (overpaint wipes the cell); a same-colour connected cell on
	 * another face becomes the multiface fallback with both; a same-colour multiface cell gains the face.
	 * The connection bits come from {@link ConnectedPaintBlock#neighbourBits} here and follow neighbour
	 * changes on their own afterwards. Returns whether anything changed.
	 */
	public static boolean paintFace(ServerLevel level, BlockPos surface, Direction face, PaintColor color) {
		return paintFace(level, surface, face, color, null);
	}

	/**
	 * The same, crediting {@code painter} with the face for {@code splat.stats.blocks} — the last painter
	 * of a face is the one who holds it. A recolour that wipes a cell forgets every face it held with it:
	 * the paint those players had is gone, and so is the credit for it.
	 */
	public static boolean paintFace(ServerLevel level, BlockPos surface, Direction face, PaintColor color,
			@Nullable UUID painter) {
		// Outside the arena's bounds nothing is painted at all: a match is won on the arena's own faces, and
		// a shot over the wall should not score. The test is on the surface rather than on the cell the paint
		// goes in, so that the inner face of a wall standing on the box's own edge is still paintable.
		if (!Arena.paintAllowed(level, surface)) return false;
		BlockState surfaceState = level.getBlockState(surface);
		if (!paintable(surfaceState)) return false;
		if (!Block.isFaceFull(surfaceState.getCollisionShape(level, surface), face)) {
			return PaintDisplays.of(level).paint(level, surface, face, color, painter);
		}
		BlockPos cell = surface.relative(face);
		Direction attach = face.getOpposite();
		int bit = 1 << attach.ordinal();
		BlockState existing = level.getBlockState(cell);
		BlockState next;
		// A cell that is wiped rather than added to: whatever it held goes, and so does who held it.
		boolean wiped = false;
		if (existing.isAir() || (existing.getBlock() instanceof Paint other && other.color() != color)) {
			next = connectedState(level, cell, attach, color);
			wiped = true;
		} else if (existing.getBlock() instanceof Paint same) {
			int mask = same.faceMask(existing);
			if ((mask & bit) != 0) return false;
			next = splatState(color, mask | bit);
		} else {
			return false;
		}
		if (!level.setBlock(cell, next, Block.UPDATE_ALL)) return false;
		PaintTally tally = PaintTally.of(level);
		tally.track(cell);
		if (wiped) tally.forget(cell);
		tally.credit(cell, attach, painter);
		// Paint blocks re-border themselves through updateShape; display quads are not blocks and get no
		// neighbour update, so the cell tells them itself.
		PaintDisplays.of(level).refreshAround(level, cell);
		return true;
	}

	/** This colour on {@code attach}, bordered against whatever its four in-plane neighbours hold now. */
	private static BlockState connectedState(ServerLevel level, BlockPos cell, Direction attach, PaintColor color) {
		BlockState state = PaintBlocks.connected(color).defaultBlockState().setValue(ConnectedPaintBlock.FACE, attach);
		return ConnectedPaintBlock.withBits(state, ConnectedPaintBlock.neighbourBits(level, cell, attach, color));
	}

	/** The multiface fallback carrying {@code mask}'s faces. */
	private static BlockState splatState(PaintColor color, int mask) {
		BlockState state = PaintBlocks.splat(color).defaultBlockState();
		for (Direction d : DIRECTIONS) state = state.setValue(MultifaceBlock.getFaceProperty(d), (mask >> d.ordinal() & 1) != 0);
		return state;
	}

	/** How often the charger's trail drops dust, and how far under it looks for a floor to paint. */
	public static final double LINE_STEP = 0.5;
	public static final double LINE_DROP = 6.0;
	/**
	 * How far along the shot the trail's crumbs start. The line is drawn from the shooter's eyes, so ink
	 * from the first blocks of it lands inside their own camera and hides the shot they are aiming.
	 * The paint under the line still starts at the eyes; only the crumbs are held back.
	 */
	public static final double LINE_DUST_START = 1.5;

	/** One crumb option per colour: the state never changes, so neither does the option. */
	private static final Map<PaintColor, BlockParticleOption> CRUMBS = new EnumMap<>(PaintColor.class);

	/**
	 * The particle every ink burst in the module is made of: vanilla's block-break crumb, carrying one
	 * of our own paint client states. Redstone dust reads as dust — a fine grey-red haze that drifts —
	 * and never looked like ink; a block crumb is a chunky lump that arcs and falls, which is what a
	 * thrown liquid does.
	 *
	 * <p>The state is {@link PaintStates#particles}, which is always a multiface donor (sculk vein for
	 * DATA, resin clump for IT). That matters: the client resolves the crumb's sprite from the state's
	 * model {@code particle} texture, which the pack points at that colour's {@code paint_<id>_15}
	 * tile, so the crumbs come out in the team colour. A redstone-wire-backed state would have gone
	 * through vanilla's {@code BlockColors} provider instead and come out tinted dark red whatever the
	 * texture said.
	 */
	public static BlockParticleOption crumbs(PaintColor color) {
		return CRUMBS.computeIfAbsent(color, c -> new BlockParticleOption(ParticleTypes.BLOCK, PaintStates.particles(c)));
	}

	/** One pillar option per colour, for the same reason {@link #CRUMBS} is cached. */
	private static final Map<PaintColor, BlockParticleOption> PILLARS = new EnumMap<>(PaintColor.class);

	/**
	 * The big ink splash: vanilla's mace-smash particle (a dust pillar) carrying a paint state. A crumb
	 * is a single small lump; a dust pillar throws a chunky column that rises and falls, which is what
	 * a squid displacing ink looks like from outside. Same paint state as {@link #crumbs}, so it comes
	 * out in the team colour rather than through a vanilla block-colour provider.
	 */
	public static BlockParticleOption pillar(PaintColor color) {
		return PILLARS.computeIfAbsent(color, c -> new BlockParticleOption(ParticleTypes.DUST_PILLAR, PaintStates.particles(c)));
	}

	/**
	 * How clear of a viewer's eyes a paint particle has to spawn, in blocks. Vanilla's {@code TerrainParticle}
	 * textures a crumb with a random <em>quarter</em> of its block state's particle sprite — four texels of our
	 * uniform 16x16 paint tile, at alpha 235 — so a crumb that spawns on a camera is one translucent
	 * team-coloured quad across the whole screen. That is the "4 pixels" of the full-screen purple overlay this
	 * clearance exists to prevent; it is not the post-effect and not vanilla's inside-a-block overlay.
	 */
	public static final double NEAR_EYES = 0.9;
	/**
	 * Vanilla's own cut-off for an unforced particle packet: a viewer farther than this is never sent one, so
	 * neither are we — {@link #burst}'s count is then the truth about who actually got it.
	 */
	public static final double PARTICLE_RANGE = 32.0;

	/**
	 * Sends a paint burst to every player in the level whose eyes are clear of it, and returns how many that
	 * was — the only thing a server-side test can see, since particles leave no trace in the level.
	 *
	 * <p>Every paint particle in the module goes out through here. A burst at a wall inches from the shooter's
	 * face, or on the body of the victim who took the shot, lands inside a camera, and {@link #NEAR_EYES} says
	 * what that means.
	 */
	public static int burst(ServerLevel level, ParticleOptions particle, Vec3 at, int count,
			double dx, double dy, double dz, double speed) {
		return burst(level, level.players(), particle, at, count, dx, dy, dz, speed);
	}

	/**
	 * The same over a given set of viewers: the sites with someone to leave out (a shooter who gets a smaller
	 * burst at the barrel, a squid whose own wake goes behind it) pass the rest of the level.
	 */
	public static int burst(ServerLevel level, List<ServerPlayer> viewers, ParticleOptions particle, Vec3 at,
			int count, double dx, double dy, double dz, double speed) {
		double spread = Math.max(dx, Math.max(dy, dz));
		int sent = 0;
		for (ServerPlayer viewer : viewers) {
			if (!clearOfEyes(viewer, at, spread)) continue;
			level.sendParticles(viewer, particle, false, false, at.x, at.y, at.z, count, dx, dy, dz, speed);
			sent++;
		}
		return sent;
	}

	/**
	 * Is a burst at {@code at} that scatters up to {@code spread} blocks worth sending to {@code viewer}: far
	 * enough from their eyes to stay off their screen, near enough that the client would draw it at all. The
	 * spread counts because a grain thrown that far from the spawn point can land on the camera on its own.
	 */
	public static boolean clearOfEyes(ServerPlayer viewer, Vec3 at, double spread) {
		double clear = NEAR_EYES + spread;
		if (viewer.getEyePosition().distanceToSqr(at) <= clear * clear) return false;
		return viewer.position().distanceToSqr(at) <= PARTICLE_RANGE * PARTICLE_RANGE;
	}

	/**
	 * The charger's trail: crumbs along the segment, and under every whole block position it passes
	 * through a look straight down for up to {@link #LINE_DROP} blocks, painting the face it lands on.
	 * That is what makes a charger shot read as a line drawn on the floor rather than as a single splat
	 * at the far end. Returns how many cells changed.
	 */
	public static int line(ServerLevel level, Vec3 from, Vec3 to, PaintColor color, @Nullable Entity source) {
		UUID painter = ownerOf(source);
		BlockParticleOption dust = crumbs(color);
		double length = from.distanceTo(to);
		int steps = (int) Math.ceil(length / LINE_STEP);
		int changed = 0;
		BlockPos last = null;
		for (int i = 0; i <= steps; i++) {
			double along = i * LINE_STEP;
			Vec3 at = length <= 0 ? from : from.lerp(to, Math.min(1.0, along / length));
			if (along >= LINE_DUST_START) burst(level, dust, at, 1, 0.02, 0.02, 0.02, 0.0);
			BlockPos here = BlockPos.containing(at);
			if (here.equals(last)) continue;
			last = here;
			BlockHitResult down = level.clip(clipContext(at, at.subtract(0, LINE_DROP, 0), source));
			if (down.getType() != HitResult.Type.BLOCK) continue;
			if (paintFace(level, down.getBlockPos(), down.getDirection(), color, painter)) changed++;
		}
		return changed;
	}

	/** A collider-only clip, from {@code source}'s point of view when there is one. */
	private static ClipContext clipContext(Vec3 from, Vec3 to, @Nullable Entity source) {
		return source != null
				? new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, source)
				: new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty());
	}

	/** Ray length from the impact point, in blocks. */
	public static final double RAY_LENGTH = 1.5;
	/** The six axis directions and the eight body diagonals, unit length. */
	public static final Vec3[] RAY_DIRECTIONS = rayDirections();

	private static Vec3[] rayDirections() {
		Vec3[] rays = new Vec3[14];
		int i = 0;
		for (Direction d : DIRECTIONS) rays[i++] = Vec3.atLowerCornerOf(d.getUnitVec3i());
		for (int x = -1; x <= 1; x += 2) {
			for (int y = -1; y <= 1; y += 2) {
				for (int z = -1; z <= 1; z += 2) rays[i++] = new Vec3(x, y, z).normalize();
			}
		}
		return rays;
	}

	/**
	 * A full impact: the 3×3 blob on the struck face, then the rays that leave the surface — of the
	 * fourteen directions, the ones that do not point back into the struck face — painting whatever
	 * face they hit (so a floor shot beside a wall also paints the wall and the corner), with a
	 * coloured dust burst and a wet sound. Returns how many cells changed.
	 *
	 * <p>A ray pointing into the surface would clip straight back through the block that was just hit
	 * and paint its far side, so paint appears behind a wall the shooter never saw. Rays lying in the
	 * plane of the face (dot 0) still fire: those are the ones that reach the wall beside a floor shot.
	 */
	public static int splash(ServerLevel level, Vec3 impact, BlockPos struck, Direction face, PaintColor color,
			RandomSource random, @Nullable Entity source) {
		return splash(level, impact, struck, face, color, random, RADIUS, source);
	}

	/** The same, with the blob's {@link #splat(ServerLevel, BlockPos, Direction, PaintColor, RandomSource, int) radius}. */
	public static int splash(ServerLevel level, Vec3 impact, BlockPos struck, Direction face, PaintColor color,
			RandomSource random, int radius, @Nullable Entity source) {
		UUID painter = ownerOf(source);
		int changed = splat(level, struck, face, color, random, radius, painter);
		Vec3 normal = Vec3.atLowerCornerOf(face.getUnitVec3i());
		Vec3 from = impact.add(normal.scale(0.05));
		// The burst is sized to the splat it goes with. A single-face droplet used to throw the same
		// twenty-four grains as a slosher's bucketful, which up close is a wall of dust in front of the
		// shooter; a single face gets four small ones and no ray dust at all.
		int weight = Math.max(0, Math.min(2, radius));
		BlockParticleOption dust = crumbs(color);
		// Half what the dust counts were: a crumb is a great deal bigger than a grain of dust, and the
		// old numbers in crumbs are a wall of ink in front of the shooter.
		int perRay = switch (weight) {
			case 0 -> 0;
			case 1 -> 1;
			default -> 2;
		};
		for (Vec3 ray : RAY_DIRECTIONS) {
			if (ray.dot(normal) < 0) continue;
			Vec3 to = from.add(ray.scale(RAY_LENGTH));
			BlockHitResult hit = level.clip(clipContext(from, to, source));
			if (hit.getType() != HitResult.Type.BLOCK) continue;
			if (paintFace(level, hit.getBlockPos(), hit.getDirection(), color, painter)) changed++;
			if (perRay == 0) continue;
			Vec3 at = hit.getLocation();
			burst(level, dust, at, perRay, 0.1, 0.1, 0.1, 0.01);
		}
		int grains = 3 + 3 * weight;
		double spread = 0.15 + 0.1 * weight;
		burst(level, dust, impact, grains, spread, spread, spread, 0.02);
		level.playSound(null, impact.x, impact.y, impact.z, SoundEvents.SLIME_BLOCK_HIT, SoundSource.BLOCKS, 0.8f, 1.3f);
		return changed;
	}
}
