package metacraft.ovvar.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JavaOps;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The patch catalogue: the art that can be sewn on. A plain patch goes on any cell ({@link Spot})
 * and is normally the cell's size ({@link Spot#PX} square); a bigger one is centred on its cell
 * and hangs over the neighbours — later-sewn on top, the way real ovvar are patched. A seat patch
 * is two cells wide and goes across the seat only, where it may be taller than the cells
 * ({@link #SEAT_HEIGHT_MAX}) and hang onto the cloth below and above them. Adding a patch is one
 * line here plus its PNG at
 * {@code art/ovvar/patches/<id>.png}, then {@code runDatagen}. The first
 * {@value Looks#INSTANT_DESIGNS} cell-sized ones can ride in the dye colour (sewn ones show at once);
 * bigger ones and later ones always go through the pack. Items store patches by id, so the order
 * is otherwise free.
 *
 * <p><b>Per-size art.</b> A patch may ship more than one PNG: the default above, plus any number of
 * {@code <id>_<w>x<h>.png} beside it — art drawn again at a size that suits a particular place,
 * which is cheaper than any amount of scaling or clipping. They are found by file name (nothing to
 * declare: the catalogue entry stays one line), and every path that draws a patch asks
 * {@link #artFor} which of them a place shows, so the item icon, the pack's placement textures, the
 * instant channel's library, the paper doll, the preview glyphs and a stand's sprites can never
 * pick different ones. {@link Fit} is the whole of the decision.
 *
 * <p><b>The sizes nobody drew.</b> A patch drawn at {@link #MAX_ART} px also gets the smaller sizes
 * it does not ship, scaled down from it ({@link #GENERATED_SIZES}); a hand-drawn file of that size
 * always wins, and a seat patch never gets any (its width is the two cells'). Nothing is written to
 * the source tree — a generated {@link Art} carries the art it comes from and whoever wants its
 * pixels scales them ({@code Tex.art(Art)}), so the artist's job is to draw the 16×16 and then draw
 * again only the sizes the scaler gets wrong.
 */
public final class Patches {
	private Patches() {}

	/**
	 * The most a patch's art may be: two cells each way, which is exactly {@link Spot#BACK_BIG}'s
	 * own size — on any other cell art this big hangs over its neighbours. Even sizes only, since
	 * the art is centred in its cell.
	 */
	public static final int MAX_ART = Spot.BIG * Spot.DETAIL;
	/**
	 * The tallest a seat patch may be: a texel of overhang above and below the seat's own row, which
	 * keeps it clear of the waistband above and of the cuff under a boot below. A seat patch is
	 * always the two cells' full width — half of it goes on each leg, so there is nowhere for a
	 * narrower one to be — but it may be this tall, centred on the cells the way oversize plain art
	 * is centred on its own cell.
	 */
	public static final int SEAT_HEIGHT_MAX = Spot.PX + 2 * Spot.DETAIL;

	/** A patch's inventory icon, and the size of the art that fills it without being scaled. */
	public static final int ICON = 16;

	/**
	 * The biggest art an ordinary cell hangs over ({@link Fit#OVER}): a cell and a half, so the art
	 * laps a quarter of a cell onto each neighbour, which is what an oversize patch is for. Art any
	 * bigger than this was drawn for the cell that is {@link #MAX_ART} square, not to hang off an
	 * ordinary one, so a patch whose own size is over this shows a smaller art there instead.
	 */
	public static final int OVER_MAX = Spot.PX * 3 / 2;

	/**
	 * The sizes generated from a {@link #MAX_ART} px art when the patch ships no drawing of its own
	 * at them: the two a {@link Fit} can ask for below {@link #MAX_ART} — {@link #OVER_MAX} square
	 * for an ordinary cell and {@link Spot#PX} square for a cell the art is clipped to.
	 */
	public static final List<Integer> GENERATED_SIZES = List.of(OVER_MAX, Spot.PX);

	/**
	 * One PNG of a patch's art: the default ({@code art/ovvar/patches/<id>.png}, the size the
	 * catalogue entry declares) or one of the per-size variants beside it
	 * ({@code art/ovvar/patches/<id>_<w>x<h>.png}). Everything that used to be measured off the
	 * patch — where the art's top-left lands on a cell, whether it hangs over — is measured off
	 * this, because which of a patch's PNGs is being drawn is decided per place ({@link #artFor}).
	 *
	 * <p>Or a size nobody drew: {@code source} is then the art it is scaled down from, and there is
	 * no PNG anywhere for it. It is named like a variant all the same, because that name is what
	 * datagen calls the textures and models it writes for it.
	 *
	 * @param byDefault is this the catalogue's own {@code <id>.png}?
	 * @param source    the art this one is scaled down from; null when it is a PNG of its own
	 */
	public record Art(String id, int width, int height, boolean byDefault, @Nullable Art source) {
		/** A PNG on the classpath: the catalogue's own art, or a variant drawn beside it. */
		Art(String id, int width, int height, boolean byDefault) {
			this(id, width, height, byDefault, null);
		}

		/** A size nobody drew: {@code source}'s pixels, scaled down when somebody asks for them. */
		static Art scaledFrom(Art source, int width, int height) {
			return new Art(source.id(), width, height, false, source);
		}

		/** Is this one of the patch's PNGs, or a size scaled down from one of them? */
		public boolean generated() {
			return source != null;
		}

		/** The name to load it by, {@code patches/<id>} or {@code patches/<id>_<w>x<h>}, without the extension. */
		public String file() {
			return byDefault ? "patches/" + id : "patches/" + id + "_" + width + "x" + height;
		}

		/**
		 * The classpath resource, which is what a variant is discovered by. A generated art has none
		 * — asking for it is a caller that means {@code Tex.art(art)} (the pixels, however they are
		 * come by) rather than a file, so say so instead of naming a path that is not there.
		 */
		public String resource() {
			if (generated()) throw new IllegalStateException(file() + " is scaled down from " + source.file() + "; there is no such file");
			return "/art/" + metacraft.ovvar.Ovvar.MOD_ID + "/" + file() + ".png";
		}

		/** Art width in cells, rounded up: what the preview library allocates a block of. */
		public int cells() {
			return (width + Spot.PX - 1) / Spot.PX;
		}

		/**
		 * Where the art's top-left lands relative to the cell's, in texture pixels: centred in the
		 * cell, which is not one size — {@link Spot#BACK_BIG} is two cells each way and the seat two
		 * cells wide — so this is asked of the cell the art is going on.
		 */
		public int offsetX(Spot spot) {
			return (spot.px() - width) / 2;
		}

		public int offsetY(Spot spot) {
			return (spot.pxHeight() - height) / 2;
		}

		/** Does the art hang over the cell it is on? */
		public boolean oversize(Spot spot) {
			return width > spot.px() || height > spot.pxHeight();
		}

		/** Does the art sit inside a {@code w}×{@code h} box whole, with nothing cut off? */
		public boolean fitsIn(int w, int h) {
			return width <= w && height <= h;
		}

		@Override
		public String toString() {
			return file() + " (" + width + "x" + height + (generated() ? ", scaled from " + source.file() : "") + ")";
		}
	}

	/**
	 * What a place asks of a patch's art — the whole of the decision, and no more than the three
	 * paths that draw a patch can tell apart (the instant channel carries a design, not a cell's
	 * choice, so its library has to hold one entry per fit):
	 *
	 * <ul>
	 *   <li>{@link #OVER}: the artist's own size, hanging over the cell if it is bigger. Every
	 *	   ordinary cell — a patch lapping onto its neighbours is the point of them — and the seat,
	 *	   whose art is drawn to the seat's own size rules already. A patch whose own size is over
	 *	   {@link #OVER_MAX} is the exception: art drawn to fill the big back cell would swallow an
	 *	   ordinary cell's neighbours whole, so there it shows the largest art that fits
	 *	   {@link #OVER_MAX} instead (a seat patch, again, is drawn to the seat's rules and is left
	 *	   alone).
	 *   <li>{@link #CLIPPED}: a cell the art is cut to, which is a box's <em>top</em> face (the
	 *	   shoulders): its four edges have no neighbouring face in the layout to continue onto. The
	 *	   art must fit the cell, so a patch with a cell-sized variant lands there whole instead of
	 *	   losing its edges.
	 *   <li>{@link #FILLED}: a cell as big as art is allowed to get ({@link #MAX_ART} square — the
	 *	   big back cell), which is meant to be filled rather than to have a smaller patch floating
	 *	   in the middle of it.
	 * </ul>
	 *
	 * <p>The ordinals are the index the instant channel's library is keyed by, and
	 * {@code OVVAR_FIT_*} in {@code ovvar.glsl} is them (a game test holds the two together).
	 */
	public enum Fit {
		OVER, CLIPPED, FILLED;

		/** What the cell {@code spot} asks of the art drawn on it. */
		public static Fit of(Spot spot) {
			if (spot.top()) return CLIPPED;
			if (spot.side != Spot.Side.SEAT && (spot.px() > Spot.PX || spot.pxHeight() > Spot.PX)) return FILLED;
			return OVER;
		}
	}

	/**
	 * @param id	 also the art file name and the item id suffix ({@code ovvar:patch_<id>})
	 * @param width  art width in pixels ({@link Spot#PX} for a cell-sized patch; a seat patch is always 2 cells wide)
	 * @param height art height in pixels
	 * @param artist who drew the art, credited in the tooltip; null when nobody is named
	 */
	public record Patch(String id, String name, boolean seat, int width, int height, String artist) {
		public Patch {
			if (seat && (width != 2 * Spot.PX || height < Spot.PX || height > SEAT_HEIGHT_MAX)) {
				throw new IllegalArgumentException(id + ": a seat patch is " + 2 * Spot.PX + " px wide and "
						+ Spot.PX + "–" + SEAT_HEIGHT_MAX + " px tall, not " + width + "×" + height);
			}
			if (width < 2 || height < 2 || width > MAX_ART || height > MAX_ART || width % 2 != 0 || height % 2 != 0) {
				throw new IllegalArgumentException(id + ": patch art must be an even size up to " + MAX_ART + "×" + MAX_ART + ", not " + width + "×" + height);
			}
		}

		/** A cell-sized patch. */
		public Patch(String id, String name) {
			this(id, name, false, Spot.PX, Spot.PX);
		}

		/** A patch bigger (or smaller) than its cell, centred on it. */
		public Patch(String id, String name, int width, int height) {
			this(id, name, false, width, height);
		}

		public Patch(String id, String name, boolean seat, int width, int height) {
			this(id, name, seat, width, height, null);
		}

		/** A seat patch exactly the two cells' size. */
		public static Patch seat(String id, String name) {
			return seat(id, name, 2 * Spot.PX, Spot.PX);
		}

		/** A seat patch, which may be taller than the cells: it is centred on them and hangs over. */
		public static Patch seat(String id, String name, int width, int height) {
			return new Patch(id, name, true, width, height);
		}

		/** The same patch, credited to an artist. */
		public Patch by(String artist) {
			return new Patch(id, name, seat, width, height, artist);
		}

		public boolean fits(Spot spot) {
			return seat == (spot == Spot.SEAT);
		}

		/** The catalogue's own PNG, {@code art/ovvar/patches/<id>.png}: the size declared above. */
		public Art art() {
			return new Art(id, width, height, true);
		}

		/**
		 * Every art this patch has, the default among them, largest last: the PNGs it ships, found
		 * by file name at class load, plus the sizes generated from a 16 px one. {@link #artFor} is
		 * what picks between them.
		 */
		public List<Art> variants() {
			return Patches.variants(this);
		}
	}


	private static final List<Patch> ALL = List.of(
			new Patch("itk", "ITK", 12, 12).by("Froosty11"),
			new Patch("nyckeln", "Nyckeln'26").by("Kexana"),
			Patch.seat("rivals", "METAcraft Rivals '26").by("Froosty11"),   // across the seat
			new Patch("it", "IT", 12, 12).by("Cactooz"),
			new Patch("data", "Data", 12, 12).by("Froosty11"),
			// Hugo/Cactooz's set, and Mackan's maid dress. New entries go last: a design's instant code
			// is its position here, so inserting one in the middle would repaint everything already sewn.
			new Patch("spiken", "Spiken", 12, 12).by("Cactooz"),
			new Patch("slaggan", "Släggan", 12, 12).by("Cactooz"),   // the file is slaggan.png: a resource id is [a-z0-9_.-]
			new Patch("ticket_to_my_heart", "Ticket to my heart", 10, 6).by("Cactooz"),   // drawn 9×6, padded to an even width
			new Patch("maid", "Maid dress", 12, 12).by("Mackan"),
			// Taller than the seat's own row: a texel of the rails hangs onto the cloth below it.
			Patch.seat("pung", "Pung", 16, 10)
	);

	private static final Map<String, Patch> BY_ID = ALL.stream()
			.collect(Collectors.toMap(Patch::id, p -> p, (a, b) -> { throw new IllegalStateException("duplicate patch id " + a.id()); }, LinkedHashMap::new));

	public static final Codec<Patch> ID_CODEC = Codec.STRING.comapFlatMap(
			id -> {
				var patch = BY_ID.get(id);
				if (patch != null) {
					return DataResult.success(patch);
				} else {
					return DataResult.error(() -> "unknown patch '" + id + "'");
				}
			},
			Patch::id
	);

	public static List<Patch> all() {
		return ALL;
	}

	/** index + 1, what the preview bits carry. */
	public static int code(Patch patch) {
		return ALL.indexOf(patch) + 1;
	}

	/** Never null: an id that is not in the catalogue is a bug (a removed entry, a typo in a command), not a state. */
	public static Patch get(String id) {
		return ID_CODEC.parse(JavaOps.INSTANCE, id).getOrThrow(IllegalArgumentException::new);
	}

	public static boolean exists(String id) {
		return BY_ID.containsKey(id);
	}

	// ---- per-size art

	/**
	 * Every patch's PNGs, found by file name once, at class load: the default, plus every
	 * {@code <id>_<w>x<h>.png} on the classpath beside it. Probing the names rather than listing
	 * the directory is what lets the same answer come out on a server, in datagen and in a jar —
	 * the size <em>is</em> the name, and there are only {@link #MAX_ART}/2 squared of them to ask
	 * about. Datagen is where a file whose name no patch could ever ask for is caught (it lists the
	 * directory and checks every entry against this), since that is where an artist finds out.
	 */
	private static final Map<String, List<Art>> VARIANTS = discoverVariants();

	private static Map<String, List<Art>> discoverVariants() {
		Map<String, List<Art>> out = new LinkedHashMap<>();
		for (Patch patch : ALL) {
			List<Art> found = new java.util.ArrayList<>();
			found.add(patch.art());
			for (int w = 2; w <= MAX_ART; w += 2) {
				for (int h = 2; h <= MAX_ART; h += 2) {
					Art art = new Art(patch.id(), w, h, false);
					if (w == patch.width() && h == patch.height()) continue;   // that is the default's own name
					if (Patches.class.getResource(art.resource()) == null) continue;
					validate(patch, art);
					found.add(art);
				}
			}
			found.addAll(generate(patch, found));
			found.sort(java.util.Comparator.comparingInt(a -> a.width() * a.height()));
			out.put(patch.id(), List.copyOf(found));
		}
		return Map.copyOf(out);
	}

	/**
	 * The {@link #GENERATED_SIZES} a patch with a {@link #MAX_ART} px art does not ship a drawing of,
	 * scaled down from that art. Nothing is written anywhere: the {@link Art} carries its source and
	 * the scaling happens wherever the pixels are wanted, which is datagen and the tests.
	 *
	 * <p>Never for a seat patch, whose art is the two cells' full width at every size it has, so a
	 * smaller one would have nowhere to sit; and never over a size somebody drew, since a drawing is
	 * always better than a scaling — that is the point of the whole arrangement.
	 */
	private static List<Art> generate(Patch patch, List<Art> drawn) {
		if (patch.seat()) return List.of();
		Art source = null;
		for (Art art : drawn) if (art.width() == MAX_ART && art.height() == MAX_ART) source = art;
		if (source == null) return List.of();
		List<Art> out = new java.util.ArrayList<>();
		for (int size : GENERATED_SIZES) {
			boolean already = false;
			for (Art art : drawn) already |= art.width() == size && art.height() == size;
			if (!already) out.add(Art.scaledFrom(source, size, size));
		}
		return out;
	}

	/**
	 * A variant that cannot be drawn is a mistake to say out loud rather than to fall back from:
	 * the even sizes and the {@link #MAX_ART} cap are the same rules the catalogue's own art
	 * follows (the art is centred in its cell, so an odd size has no place to sit), and a seat
	 * variant is the seat's full width for the same reason a seat patch is — half of it goes on
	 * each leg, so there is nowhere for a narrower one to be.
	 */
	private static void validate(Patch patch, Art art) {
		if (patch.seat() && art.width() != 2 * Spot.PX) {
			throw new IllegalArgumentException(art.file() + ": a seat patch's art is " + 2 * Spot.PX
					+ " px wide, so a variant of it cannot be " + art.width() + " px wide");
		}
		if (patch.seat() && art.height() > SEAT_HEIGHT_MAX) {
			throw new IllegalArgumentException(art.file() + ": a seat patch's art is at most " + SEAT_HEIGHT_MAX + " px tall");
		}
	}

	/** Every art a patch has — the PNGs it ships and the sizes generated from them — smallest first. */
	public static List<Art> variants(Patch patch) {
		return VARIANTS.get(patch.id());
	}

	/**
	 * Which of a patch's arts a cell shows: the largest that fits what the cell asks for
	 * ({@link Fit}), and the default when none of them does — which is exactly today's behaviour
	 * for a patch that has only the one art, and for one whose variants are all too big for a
	 * face the art is clipped to.
	 *
	 * <p>So a 12×12 patch with an 8×8 and a 16×16 variant lands on a shoulder as the 8×8 (whole,
	 * not clipped), on the big back cell as the 16×16 (filling it), on an ordinary chest cell as
	 * the 12×12 it was drawn as (hanging over its neighbours, by design) and in the inventory as
	 * the 16×16 (unscaled). A patch drawn 16×16 in the catalogue instead — nothing smaller drawn,
	 * so the 12×12 and the 8×8 are both generated — lands on an ordinary chest cell as the 12×12
	 * rather than blanketing the cells round it. Every path asks this, so none of them can draw a
	 * different one.
	 */
	public static Art artFor(Patch patch, Spot spot) {
		return artFor(patch, Fit.of(spot));
	}

	/** The same by fit alone, which is how the instant channel's library is keyed. */
	public static Art artFor(Patch patch, Fit fit) {
		return switch (fit) {
			// The artist's own size, which for a seat patch is the seat's own rules and for anything
			// that laps no further than OVER_MAX over its cell is as drawn; art bigger than that was
			// drawn to fill the big back cell, so an ordinary cell takes the largest one that laps.
			case OVER -> patch.seat() || patch.art().fitsIn(OVER_MAX, OVER_MAX) ? patch.art() : largestIn(patch, OVER_MAX, OVER_MAX);
			case CLIPPED -> largestIn(patch, Spot.PX, Spot.PX);
			case FILLED -> largestIn(patch, MAX_ART, MAX_ART);
		};
	}

	/** A patch's inventory icon: the art that fills the {@value #ICON} px sprite without scaling, if it has one. */
	public static Art iconArt(Patch patch) {
		return largestIn(patch, ICON, ICON);
	}

	/** The largest of a patch's arts that sits in a {@code w}×{@code h} box whole; the default if none does. */
	private static Art largestIn(Patch patch, int w, int h) {
		Art best = null;
		for (Art art : variants(patch)) {
			if (!art.fitsIn(w, h)) continue;
			if (best == null || art.width() * art.height() > best.width() * best.height()) best = art;
		}
		return best == null ? patch.art() : best;
	}
}
