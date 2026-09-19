package nu.metacraft.rivals;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Where a team starts and where the arena ends: one {@link SavedData} per level, id {@code rivals_arena}.
 *
 * <p>Two things live here. A <b>spawn</b> per team — position, yaw and pitch, set by standing where the
 * team should appear and facing the way they should face, because that is the only way to choose a look
 * direction that does not involve typing two numbers. And an optional <b>box</b>, two corners: while one
 * is set, paint outside it is refused ({@link nu.metacraft.rivals.paint.Painter#paintFace},
 * {@link nu.metacraft.rivals.paint.PaintDisplays#paint}) and {@code /rivals reset} clears only what is
 * inside it, so a match cannot be won by painting the car park.
 *
 * <p>Per level rather than per server: an arena is a place, and two levels can each hold one. Saved,
 * unlike the tally and the display quads, because setting up an arena is work an operator does once and
 * should not have to do again after a restart.
 *
 * <p>The box test is on the <b>surface</b> block, not on the cell the paint goes in: a wall on the box's
 * own edge has its inner face painted into the cell beyond it, and refusing that would leave the arena's
 * own boundary wall unpaintable from the inside.
 */
public final class Arena extends SavedData {
	/** A team's starting point: where they stand and where they look. */
	public record Spawn(Vec3 pos, float yaw, float pitch) {
		public static final Codec<Spawn> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Vec3.CODEC.fieldOf("pos").forGetter(Spawn::pos),
				Codec.FLOAT.fieldOf("yaw").forGetter(Spawn::yaw),
				Codec.FLOAT.fieldOf("pitch").forGetter(Spawn::pitch)
		).apply(instance, Spawn::new));

		/** Where a player standing here is, as a block position, for the messages that print it. */
		public BlockPos block() {
			return BlockPos.containing(pos);
		}

		@Override
		public String toString() {
			return String.format("%.1f %.1f %.1f facing %.0f/%.0f", pos.x, pos.y, pos.z, yaw, pitch);
		}
	}

	public static final Codec<Arena> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.unboundedMap(Codec.STRING, Spawn.CODEC).fieldOf("spawns").forGetter(Arena::spawnsById),
			BoundingBox.CODEC.optionalFieldOf("box").forGetter(arena -> Optional.ofNullable(arena.box))
	).apply(instance, Arena::fromSaved));

	private static final SavedDataType<Arena> TYPE = new SavedDataType<>(
			Rivals.id("rivals_arena"), Arena::new, CODEC, null);

	private final Map<PaintColor, Spawn> spawns = new EnumMap<>(PaintColor.class);
	private @Nullable BoundingBox box;

	public Arena() {}

	private static Arena fromSaved(Map<String, Spawn> spawns, Optional<BoundingBox> box) {
		Arena arena = new Arena();
		spawns.forEach((id, spawn) -> PaintColor.byId(id).ifPresent(color -> arena.spawns.put(color, spawn)));
		arena.box = box.orElse(null);
		return arena;
	}

	private Map<String, Spawn> spawnsById() {
		Map<String, Spawn> out = new HashMap<>();
		spawns.forEach((color, spawn) -> out.put(color.id, spawn));
		return out;
	}

	public static Arena of(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(TYPE);
	}

	public Optional<Spawn> spawn(PaintColor color) {
		return Optional.ofNullable(spawns.get(color));
	}

	/** Take this player's stance as the team's spawn. */
	public void setSpawn(PaintColor color, ServerPlayer player) {
		setSpawn(color, new Spawn(player.position(), player.getYRot(), player.getXRot()));
	}

	public void setSpawn(PaintColor color, Spawn spawn) {
		spawns.put(color, spawn);
		setDirty();
	}

	/** Is every team's spawn set? What a match start needs before it can teleport anybody. */
	public boolean spawnsReady() {
		for (PaintColor color : PaintColor.values()) {
			if (!spawns.containsKey(color)) return false;
		}
		return true;
	}

	public Optional<BoundingBox> box() {
		return Optional.ofNullable(box);
	}

	public void setBox(BlockPos from, BlockPos to) {
		box = BoundingBox.fromCorners(from, to);
		setDirty();
	}

	/** Take the bounds off: the whole level is the arena again. Returns whether there was one. */
	public boolean clearBox() {
		boolean had = box != null;
		box = null;
		setDirty();
		return had;
	}

	/**
	 * May paint go on this block? Everything is inside when no box is set, which is what an arena with no
	 * bounds means — and what every level that has never been set up means.
	 */
	public boolean inside(BlockPos pos) {
		return box == null || box.isInside(pos);
	}

	/** The same for a level, which is the form the paint paths want: one lookup, no Optional. */
	public static boolean paintAllowed(ServerLevel level, BlockPos surface) {
		return of(level).inside(surface);
	}

	public void forget() {
		spawns.clear();
		box = null;
		setDirty();
	}

	// ---- /rivals arena show: the box drawn in particles, for ten seconds

	/** How long a {@code show} lasts, and how often it redraws. */
	public static final int SHOW_TICKS = 200;
	private static final int SHOW_EVERY = 10;
	/** One point per this many blocks along an edge, and never more points than this in one draw. */
	private static final double EDGE_STEP = 1.0;
	private static final int MAX_POINTS = 600;

	/** Who is being shown which box, and until when (absolute server ticks). */
	private static final Map<ServerPlayer, Long> SHOWING = new HashMap<>();

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (SHOWING.isEmpty() || server.getTickCount() % SHOW_EVERY != 0) return;
			long now = server.getTickCount();
			SHOWING.entrySet().removeIf(entry -> entry.getValue() <= now || entry.getKey().hasDisconnected());
			for (ServerPlayer viewer : List.copyOf(SHOWING.keySet())) {
				if (!(viewer.level() instanceof ServerLevel level)) continue;
				of(level).box().ifPresent(box -> drawOutline(level, viewer, box));
			}
		});
	}

	/**
	 * Show {@code viewer} the level's box for {@link #SHOW_TICKS}. Returns whether there was a box to
	 * show. Per viewer, the way every paint burst goes out: an outline is a thing one person asked for.
	 */
	public static boolean show(ServerLevel level, ServerPlayer viewer) {
		if (of(level).box().isEmpty()) return false;
		SHOWING.put(viewer, level.getServer().getTickCount() + (long) SHOW_TICKS);
		return true;
	}

	/** For tests and for a server stop: nobody is being shown anything. */
	public static void clearShows() {
		SHOWING.clear();
	}

	/** How many players are being shown an outline. For tests. */
	public static int shows() {
		return SHOWING.size();
	}

	/**
	 * The twelve edges of the box, a point every {@link #EDGE_STEP} blocks, sent to one viewer. End rods
	 * rather than paint crumbs: this is scaffolding an operator is looking at, not ink, and it should not
	 * read as either team's colour.
	 */
	static int drawOutline(ServerLevel level, ServerPlayer viewer, BoundingBox box) {
		List<Vec3> points = outlinePoints(box);
		int sent = 0;
		for (Vec3 at : points) {
			level.sendParticles(viewer, ParticleTypes.END_ROD, false, false, at.x, at.y, at.z, 1, 0, 0, 0, 0);
			sent++;
		}
		return sent;
	}

	/** The points along the box's twelve edges, in world coordinates, capped at {@link #MAX_POINTS}. */
	public static List<Vec3> outlinePoints(BoundingBox box) {
		double x0 = box.minX(), y0 = box.minY(), z0 = box.minZ();
		double x1 = box.maxX() + 1.0, y1 = box.maxY() + 1.0, z1 = box.maxZ() + 1.0;
		List<Vec3> points = new ArrayList<>();
		// A step that grows with the box, so a hundred-block arena does not ask for ten thousand particles.
		double step = Math.max(EDGE_STEP, ((x1 - x0) + (y1 - y0) + (z1 - z0)) * 4 / MAX_POINTS);
		for (double x = x0; x <= x1; x += step) {
			points.add(new Vec3(x, y0, z0));
			points.add(new Vec3(x, y0, z1));
			points.add(new Vec3(x, y1, z0));
			points.add(new Vec3(x, y1, z1));
		}
		for (double y = y0; y <= y1; y += step) {
			points.add(new Vec3(x0, y, z0));
			points.add(new Vec3(x0, y, z1));
			points.add(new Vec3(x1, y, z0));
			points.add(new Vec3(x1, y, z1));
		}
		for (double z = z0; z <= z1; z += step) {
			points.add(new Vec3(x0, y0, z));
			points.add(new Vec3(x0, y1, z));
			points.add(new Vec3(x1, y0, z));
			points.add(new Vec3(x1, y1, z));
		}
		return points;
	}
}
