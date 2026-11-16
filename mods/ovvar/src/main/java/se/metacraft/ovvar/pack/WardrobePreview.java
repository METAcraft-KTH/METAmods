package se.metacraft.ovvar.pack;

import se.metacraft.ovvar.Ovvar;
import se.metacraft.ovvar.content.Chapter;
import se.metacraft.ovvar.content.Patches;
import se.metacraft.ovvar.content.Piece;
import se.metacraft.ovvar.content.Placement;
import se.metacraft.ovvar.content.Spot;
import se.metacraft.ovvar.datagen.GeneratedAssets;
import se.metacraft.ovvar.datagen.Tex;
import se.metacraft.ovvar.pack.WardrobeFont.Glyph;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The wardrobe screen's preview: the player's whole ovve — top and trousers as one figure — drawn
 * in the screen's title, from four sides, and turned by the buttons at the end of the tab row.
 *
 * <p>A vanilla client cannot draw an entity inside a chest screen, so the preview is a
 * <em>paper doll</em>: the humanoid model's faces, cut out of the very equipment-layer textures
 * the client draws the garment with, laid out flat as a standing figure. Front and back show the
 * arms beside the torso and the legs below it; a side shows the body's own side face with the
 * sleeve beside it and the trouser leg below, which is where that side's cells are.
 *
 * <p><b>It is built up in layers, not baked per design.</b> One glyph per (chapter, angle) draws
 * the bare garment, and one small glyph per (patch, cell, angle) draws that patch exactly where it
 * lands on the doll — cropped to its own art, so the glyph is a dozen pixels square, and placed by
 * space advances and its ascent. The title stacks the bare ovve and then one glyph per sewn
 * placement, so a design is composed at the moment the screen opens: the pack holds a fixed
 * {@code chapters × 4 + Σ patches-per-cell × 1} glyphs, never grows with what anybody sews, and
 * nothing has to be regenerated or pushed when a patch goes on. A cell is on exactly one face of
 * one box, and a face is seen from exactly one of the four angles, so a cell has exactly one glyph
 * per patch — and the cells you cannot see from an angle simply have none.
 *
 * <p>Sizes: the source textures hold {@link Spot#DETAIL} texels per skin pixel, but every face is
 * first dropped back to the art's own {@link Spot#ART_DETAIL} — lossless, since datagen scaled the
 * art up by exactly that factor — and the doll is drawn at {@value #PX} screen px per skin pixel,
 * so every face is resampled ×1.5 from art (which keeps every texel the patch art has, at the
 * price of every other column being 2 px wide). Everything that measures where art lands on the
 * doll therefore counts in art pixels, {@link #AD} per skin texel; {@link #D} is only for cutting
 * a face out of the texture. The bare
 * doll is shaded — the viewer's right darker, either arm darker again, a seam at the waist — and
 * given a 1 px dark outline drawn on its own outermost pixels, so it reads as a figure and not as
 * a strip of faces; a patch layer takes the same shading, and keeps off the outline's own pixels so
 * a patch at the edge of a sleeve cannot break the figure's edge.
 */
public final class WardrobePreview {
	private WardrobePreview() {}

	/** The preview panel: rows 1-4, cols 5-8 of the screen, its cell ring included. */
	public static final int PANEL_X = WardrobeFont.PREVIEW_PANEL_X, PANEL_Y = WardrobeFont.PANEL_Y;
	public static final int PANEL_W = WardrobeFont.PREVIEW_PANEL_W, PANEL_H = WardrobeFont.PANEL_H;

	// ---- the sides you can look from

	/** Which way round the doll is turned. A cell is visible from exactly one of these. */
	public enum Angle {
		FRONT("Front"), RIGHT("Their right"), BACK("Back"), LEFT("Their left");

		public final String label;

		Angle(String label) {
			this.label = label;
		}

		/** The next angle {@code turn} steps round (−1 = left, +1 = right). */
		public Angle turned(int turn) {
			Angle[] all = values();
			return all[(ordinal() + turn + all.length) % all.length];
		}
	}

	// ---- the figure's geometry

	private static final int D = Spot.DETAIL;
	/** Art pixels per skin texel: the resolution every face is at once {@link #blit} has cut it out. */
	private static final int AD = Spot.ART_DETAIL;
	/** Every box's side faces, the rows every cell is on: skin rows 20-32, 12 px tall ({@link Spot#FACE_ROW}). */
	private static final int FACE_ROW = Spot.FACE_ROW, FACE_ROWS = Spot.FACE_ROWS;
	private static final int FACE_V = FACE_ROW * D, FACE_H = FACE_ROWS * D;
	/** Screen px per skin px. */
	private static final int PX = 3;
	private static final int FACE = FACE_ROWS * PX;
	/** A limb face is 4 skin px across, the body's front and back 8, the body's sides 4. */
	private static final int LIMB = 4, TORSO = 8;
	private static final int LIMB_PX = LIMB * PX, TORSO_PX = TORSO * PX;
	/** Transparent px between the parts: what makes a flat set of faces read as arms, a body and legs. */
	private static final int GAP = 1;

	/** The faces of each box, by the skin x their strip starts at (see {@link Spot}'s own note). */
	private static final int BODY_RIGHT = 16, BODY_FRONT = 20, BODY_LEFT = 28, BODY_BACK = 32;
	private static final int ARM_OUT = 40, ARM_FRONT = 44, ARM_BACK = 52;
	private static final int LEG_OUT = 0, LEG_FRONT = 4, LEG_BACK = 12;

	/** How far towards black the viewer's right half of the figure goes, as if lit from the left. */
	private static final double SHADE = 0.15;
	/** A sleeve is a narrow box turning away from the viewer: this much darker again. */
	private static final double LIMB_SHADE = 0.12;
	/** How far towards black the silhouette's outline goes — the panel's cloth is the chapter colour too. */
	private static final double OUTLINE = 0.78;
	/** And the seam at the waist, the one part boundary the gaps between the parts do not draw. */
	private static final double SEAM = 0.5;

	/**
	 * One face of one box, laid flat: which half's texture it comes from, the face's skin x and
	 * width, whose side of the body it is (which decides the cells that show on it), whether the
	 * model draws it mirrored, where it goes in the glyph, and whether it is a sleeve.
	 */
	private record Part(Piece piece, int u, int w, Spot.Side side, boolean mirror, int x, int y, boolean sleeve) {
		int widthPx() {
			return w * PX;
		}
	}

	private static Part body(int u, int w, boolean mirror, int x, int y) {
		return new Part(Piece.TOP, u, w, Spot.Side.BODY, mirror, x, y, false);
	}

	private static Part arm(int u, Spot.Side side, boolean mirror, int x) {
		return new Part(Piece.TOP, u, LIMB, side, mirror, x, 0, true);
	}

	private static Part leg(int u, Spot.Side side, boolean mirror, int x) {
		return new Part(Piece.BOTTOM, u, LIMB, side, mirror, x, FACE, false);
	}

	/** Where a figure {@code w} px wide starts, centred in the panel. */
	private static int centred(int w) {
		return (PANEL_W - w) / 2;
	}

	private static final int WIDE = 2 * LIMB_PX + 2 * GAP + TORSO_PX, NARROW = 2 * LIMB_PX + GAP;
	private static final int WIDE_X = centred(WIDE), NARROW_X = centred(NARROW);

	/**
	 * The parts of each angle. The model draws the wearer's left limbs as mirror images of the
	 * right limbs' strips (and datagen mirrors a left cell's art to suit), so every left limb here
	 * is drawn flipped; and the wearer's right is on the viewer's left from the front and on the
	 * viewer's right from behind, which is why the two swap ends.
	 */
	private static List<Part> parts(Angle angle) {
		int torsoX = WIDE_X + LIMB_PX + GAP, farArmX = torsoX + TORSO_PX + GAP;
		int nearLegX = torsoX, farLegX = torsoX + LIMB_PX + GAP;
		return switch (angle) {
			case FRONT -> List.of(
					arm(ARM_FRONT, Spot.Side.RIGHT, false, WIDE_X),
					body(BODY_FRONT, TORSO, false, torsoX, 0),
					arm(ARM_FRONT, Spot.Side.LEFT, true, farArmX),
					leg(LEG_FRONT, Spot.Side.RIGHT, false, nearLegX),
					leg(LEG_FRONT, Spot.Side.LEFT, true, farLegX));
			case BACK -> List.of(
					arm(ARM_BACK, Spot.Side.LEFT, true, WIDE_X),
					body(BODY_BACK, TORSO, false, torsoX, 0),
					arm(ARM_BACK, Spot.Side.RIGHT, false, farArmX),
					leg(LEG_BACK, Spot.Side.LEFT, true, nearLegX),
					leg(LEG_BACK, Spot.Side.RIGHT, false, farLegX));
			// A side: the body's own side face, the sleeve beside it, the trouser leg below it —
			// the cells of that side are all on the sleeve's and the leg's outer faces.
			case RIGHT -> List.of(
					body(BODY_RIGHT, LIMB, false, NARROW_X, 0),
					arm(ARM_OUT, Spot.Side.RIGHT, false, NARROW_X + LIMB_PX + GAP),
					leg(LEG_OUT, Spot.Side.RIGHT, false, NARROW_X));
			case LEFT -> List.of(
					arm(ARM_OUT, Spot.Side.LEFT, true, NARROW_X),
					body(BODY_LEFT, LIMB, false, NARROW_X + LIMB_PX + GAP, 0),
					leg(LEG_OUT, Spot.Side.LEFT, true, NARROW_X + LIMB_PX + GAP));
		};
	}

	/** The face of its box a cell sits on, as {skin x, width}. */
	private static int[] face(Spot spot) {
		if (spot.u >= BODY_RIGHT && spot.u < ARM_OUT) {   // the body's strip: right 4, front 8, left 4, back 8
			if (spot.u < BODY_FRONT) return new int[]{BODY_RIGHT, LIMB};
			if (spot.u < BODY_LEFT) return new int[]{BODY_FRONT, TORSO};
			if (spot.u < BODY_BACK) return new int[]{BODY_LEFT, LIMB};
			return new int[]{BODY_BACK, TORSO};
		}
		int strip = spot.u < BODY_RIGHT ? 0 : ARM_OUT;    // a limb's strip: four 4-wide faces
		return new int[]{strip + (spot.u - strip) / LIMB * LIMB, LIMB};
	}

	/** Does this part draw the cell {@code spot}? (The seat is on both legs' back faces.) */
	private static boolean shows(Part part, Spot spot) {
		int[] face = face(spot);
		if (part.piece() != spot.piece || part.u() != face[0] || part.w() != face[1]) return false;
		if (spot.side == Spot.Side.SEAT) return part.side() == Spot.Side.RIGHT || part.side() == Spot.Side.LEFT;
		return part.side() == spot.side;
	}

	/**
	 * The angle a cell is seen from, or null if the doll never shows it (the inner faces). A cell is
	 * on one face of one box and a face is seen from exactly one of the four angles, so there is
	 * never more than the one.
	 */
	public static @Nullable Angle angleOf(Spot spot) {
		for (Angle angle : Angle.values()) {
			for (Part part : parts(angle)) {
				if (shows(part, spot)) return angle;
			}
		}
		return null;
	}

	// ---- where a cell lands on the figure, in px

	/** Every angle's cell rectangles, worked out once from the same geometry the compositor draws with. */
	private static final Map<Angle, Map<Spot, int[]>> CELL_RECTS = new LinkedHashMap<>();

	static {
		for (Angle angle : Angle.values()) {
			Map<Spot, int[]> bySpot = new LinkedHashMap<>();
			for (Spot spot : Spot.values()) {
				int[] rect = computeCellRect(angle, spot);
				if (rect != null) bySpot.put(spot, rect);
			}
			CELL_RECTS.put(angle, bySpot);
		}
	}

	/**
	 * The pixel rectangle {x, y, w, h} inside the {@value #PANEL_W}×{@value #PANEL_H} preview glyph
	 * that this angle draws the cell {@code spot} in, or null if the angle does not show the cell.
	 *
	 * <p>It is not a second set of numbers beside the compositor's: it is measured by putting a cell
	 * -shaped mask through {@link #blit} — the very call {@link #patchArt} places a patch with, so
	 * the per-part offset, the model's mirroring and the ×1.5 resample are the same arithmetic by
	 * construction and cannot drift from the picture. The seat, which is on both legs' back faces,
	 * gives the rectangle across the two of them.
	 */
	public static int @Nullable [] cellRect(Angle angle, Spot spot) {
		return CELL_RECTS.get(angle).get(spot);
	}

	private static int @Nullable [] computeCellRect(Angle angle, Spot spot) {
		Tex mask = cellMask(spot);
		Tex canvas = Tex.blank(PANEL_W, PANEL_H);
		boolean shown = false;
		for (Part part : parts(angle)) {
			if (!shows(part, spot)) continue;
			canvas = blit(canvas, part, mask);
			shown = true;
		}
		return shown ? bounds(canvas) : null;
	}

	/**
	 * The part of a patch's own art a cell's glyph can show, laid out the way it reads across the
	 * figure — what an audit compares that glyph against (see {@code WardrobeSheet}'s audit and the
	 * {@code wardrobePreviewDrawsEveryCellsOwnPatchArt} game test).
	 *
	 * <p>It is not the whole art, and that is not a crop going wrong. A patch bigger than its cell
	 * hangs over the cell's edges, and datagen wraps what hangs over <em>round the box</em> (round the
	 * part's strip, as the shader samples it), so the columns that land past the end of the cell's own
	 * face are drawn on the neighbouring face — a face the doll draws from another angle, or not at
	 * all — and the rows that leave the box's side rows are dropped altogether. Nor does a face draw
	 * all of the texels it does keep: {@link #keptOffTheOutline} takes the ring the figure's outline
	 * owns off the patch layer, and the resample gives the far column and the bottom row of a face a
	 * single screen pixel each, which is that ring's.
	 *
	 * <p>The seat is one patch across both legs' back faces, so it is a part each. Seat art is drawn as
	 * seen from behind — the only way anybody sees a seat — so it reads across the figure the way it
	 * was drawn: the art's left half on the leg at the viewer's left, which from behind is the
	 * wearer's left leg, and that is the leg {@link Spot#seatHalf} gives it. The reference takes the
	 * half that belongs where each leg is, rather than reading the cut back off {@code seatHalf}: a
	 * cut that put a half on the wrong leg would otherwise agree with itself and pass.
	 *
	 * <p>{@code chosen} says which of the patch's PNGs this cell shows ({@link Patches#artFor}) and
	 * {@code art} holds its pixels: a patch may be drawn at more than one size, and where the art
	 * lands on the cell — hence the window — follows the size of the one really drawn there.
	 */
	public static Tex shownArt(Spot spot, Patches.Art chosen, Tex art) {
		Angle angle = angleOf(spot);
		if (angle == null) return Tex.blank(art.width, art.height);
		List<Part> drawn = new ArrayList<>();
		for (Part part : parts(angle)) if (shows(part, spot)) drawn.add(part);
		if (drawn.isEmpty()) return Tex.blank(art.width, art.height);
		drawn.sort(java.util.Comparator.comparingInt(Part::x));
		int slice = art.width / drawn.size();   // the seat is a half per leg; every other cell is one piece
		Tex out = Tex.blank(art.width, art.height);
		for (int i = 0; i < drawn.size(); i++) {
			// Each part takes the slice of the art that belongs where it is: the art in its own order
			// across the figure. That is the whole of what the convention says, so it is what the
			// reference says too, rather than reading the cut back off {@link Spot#seatHalf} — a cut
			// that put a half on the wrong leg would then agree with itself and pass.
			Tex piece = onThePart(spot, chosen, drawn.get(i), art.crop(i * slice, 0, slice, art.height));
			out = out.blit(piece, 0, 0, piece.width, piece.height, i * slice, 0);
		}
		return out;
	}

	/** One part's share of a patch's art: the columns and rows of it that part's face really draws. */
	private static Tex onThePart(Spot spot, Patches.Art chosen, Part part, Tex piece) {
		// Datagen pre-mirrors the art of a limb the model mirrors, and the doll mirrors that limb for
		// the same reason — so the art is windowed in the mirrored order and put back afterwards.
		Tex baked = part.mirror() ? piece.flipX() : piece;
		// All in art pixels: blit drops the face to art resolution before it resamples, so the art's
		// pixels and the face's are 1:1 here, as they are on the texture scaled by ART_SCALE.
		int x = spot.u * AD + chosen.artOffsetX(spot), y = spot.v * AD + chosen.artOffsetY(spot);
		int strip = Spot.stripStart(spot) * AD, stripWidth = Spot.stripWidth(spot) * AD;
		int texels = part.w() * AD, rows = FACE_ROWS * AD;
		Tex out = Tex.blank(baked.width, baked.height);
		for (int ax = 0; ax < baked.width; ax++) {
			// The overhang wraps round the part's strip and lands on the face next door.
			int column = strip + Math.floorMod(x + ax - strip, stripWidth) - part.u() * AD;
			if (column < 0 || column >= texels) continue;   // it landed on the face next door
			int across = part.mirror() ? texels - 1 - column : column;   // in the order the figure reads
			if (!drawnAcross(across, texels, part.widthPx())) continue;
			for (int ay = 0; ay < baked.height; ay++) {
				int row = y + ay - FACE_ROW * AD;
				if (row < 0 || row >= rows) continue;
				if (!drawnAcross(row, rows, FACE)) continue;
				out = out.with(ax, ay, baked.get(ax, ay));
			}
		}
		return part.mirror() ? out.flipX() : out;
	}

	/**
	 * Does a face's texel at this position along it reach the screen, or does the figure's outline own
	 * the only pixel it gets? A face is resampled to more px than it has texels, so most texels get
	 * two of them — but the last position along gets one, and that one is the ring
	 * {@link #keptOffTheOutline} takes off every patch layer.
	 */
	private static boolean drawnAcross(int position, int texels, int px) {
		for (int at = 1; at < px - 1; at++) if (at * texels / px == position) return true;
		return false;
	}

	/** A layer texture opaque over exactly one cell's own texels and transparent everywhere else. */
	private static Tex cellMask(Spot spot) {
		int w = 64 * D, h = 32 * D;
		int[] px = new int[w * h];
		for (int y = spot.v * D; y < spot.v * D + spot.pxHeight(); y++) {
			for (int x = spot.u * D; x < spot.u * D + spot.px(); x++) px[y * w + x] = 0xFFFFFFFF;
		}
		return Tex.of(w, h, px);
	}

	// ---- the glyphs

	/**
	 * Every equipment layer texture this has read, by pack path. Declared before the block that
	 * fills the glyph registry, which reads a good few of them.
	 */
	private static final Map<String, Tex> CACHE = new LinkedHashMap<>();


	private static final Map<Chapter, Map<Angle, Glyph>> BARE = new LinkedHashMap<>();
	/** {@code angle + "/" + spot + "." + patch id} → its glyph, on the one angle that draws the cell. */
	private static final Map<String, Glyph> PATCHES = new LinkedHashMap<>();

	static {
		for (Chapter chapter : Chapter.values()) {
			Map<Angle, Glyph> byAngle = new LinkedHashMap<>();
			for (Angle angle : Angle.values()) {
				byAngle.put(angle, WardrobeFont.glyph("preview/" + chapter.id + "_" + angle.name().toLowerCase(java.util.Locale.ROOT),
						PANEL_X, PANEL_Y, PANEL_W, PANEL_H, () -> bareArt(chapter, angle)));
			}
			BARE.put(chapter, byAngle);
		}
		for (Spot spot : Spot.values()) {
			Angle angle = angleOf(spot);
			if (angle == null) continue;   // a face the doll never shows
			for (Patches.Patch patch : Patches.all()) {
				if (!patch.fits(spot)) continue;
				Placement placement = new Placement(spot, patch);
				Tex art = patchArt(placement, angle);
				int[] box = bounds(art);
				if (box == null) continue;   // nothing of this patch shows on that face
				Tex cropped = art.crop(box[0], box[1], box[2], box[3]);
				PATCHES.put(key(placement, angle), WardrobeFont.glyph("preview/patch/" + spot.id() + "_" + patch.id(),
						PANEL_X + box[0], PANEL_Y + box[1], box[2], box[3], () -> cropped));
			}
		}
		Ovvar.LOGGER.info("[ovvar] wardrobe preview: {} bare ovve glyph(s) ({} × {} angles) and {} patch glyph(s)",
				Chapter.values().length * Angle.values().length, Chapter.values().length, Angle.values().length, PATCHES.size());
	}

	/**
	 * Registers every glyph (the class initialiser does the work; this is what makes sure it has
	 * run before a pack is built rather than when the first screen opens).
	 */
	public static void init() {
		// deliberately empty
	}

	private static String key(Placement placement, Angle angle) {
		return angle.name() + "/" + placement.key();
	}

	public static int bareGlyphCount() {
		return Chapter.values().length * Angle.values().length;
	}

	public static int patchGlyphCount() {
		return PATCHES.size();
	}

	public static int glyphCount() {
		return bareGlyphCount() + patchGlyphCount();
	}

	public static Glyph bareGlyph(Chapter chapter, Angle angle) {
		return BARE.get(chapter).get(angle);
	}

	/**
	 * The glyph that draws this placement on the doll from the angle {@link #angleOf} names, or null
	 * if the doll cannot show that cell.
	 */
	public static @Nullable Glyph patchGlyph(Placement placement) {
		Angle angle = angleOf(placement.spot());
		return angle == null ? null : patchGlyph(placement, angle);
	}

	/** The glyph for one angle, or null if that angle's figure does not draw the cell. */
	public static @Nullable Glyph patchGlyph(Placement placement, Angle angle) {
		return PATCHES.get(key(placement, angle));
	}

	/**
	 * The whole preview for a design, ready to append to the title: the bare ovve from this angle,
	 * then every placement this angle shows, each at its own place on the figure.
	 */
	public static Component glyphs(Chapter chapter, Angle angle, List<Placement> placements) {
		List<Glyph> drawn = new ArrayList<>();
		drawn.add(bareGlyph(chapter, angle));
		// In the stack's own order (Spot.layer), the same as the garment's layers: cells overlap, and
		// the glyphs are drawn one over another in the order the title holds them.
		for (Placement placement : Spot.stacked(placements)) {
			Glyph glyph = patchGlyph(placement, angle);
			if (glyph != null) drawn.add(glyph);
		}
		return WardrobeFont.drawn(drawn.toArray(new Glyph[0]));
	}

	// ---- drawing the art

	/** The bare garment from one side: both halves' cloth, laid out, shaded, seamed and outlined. */
	public static Tex bareArt(Chapter chapter, Angle angle) {
		Tex cloth = cloth(chapter);
		Tex canvas = Tex.blank(PANEL_W, PANEL_H);
		List<Part> parts = parts(angle);
		for (Part part : parts) canvas = blit(canvas, part, cloth);
		return outlined(waisted(shaded(canvas, parts), parts));
	}

	/** One placement, on a transparent canvas the size of the panel, at the place the doll draws it. */
	public static Tex patchArt(Placement placement, Angle angle) {
		Tex canvas = Tex.blank(PANEL_W, PANEL_H);
		List<Part> parts = parts(angle);
		for (Part part : parts) {
			if (!shows(part, placement.spot())) continue;
			canvas = blit(canvas, part, placementTexture(placement, part));
		}
		return keptOffTheOutline(shaded(canvas, parts), parts);
	}

	/** One face of {@code layer}, mirrored if the model mirrors it, at screen size, onto the canvas. */
	private static Tex blit(Tex canvas, Part part, Tex layer) {
		Tex face = layer.crop(part.u() * D, FACE_V, part.w() * D, FACE_H);
		// Back to art resolution before anything else touches it. The doll is drawn at PX screen px
		// per skin px, which is finer than the art but coarser than the texture once DETAIL is raised,
		// and resampling straight from the texture would be a downscale that softens pixel art the
		// doll is meant to show exactly. Dropping ART_SCALE first is lossless — everything on this
		// layer was scaled up from art by precisely that factor, so each block is one flat colour —
		// and it leaves the resample below the same ×1.5 upscale it has always been.
		if (Spot.ART_SCALE > 1) {
			face = face.downscaled(face.width / Spot.ART_SCALE, face.height / Spot.ART_SCALE);
		}
		if (part.mirror()) face = face.flipX();
		face = face.resampled(part.widthPx(), FACE);
		return canvas.blit(face, 0, 0, face.width, face.height, part.x(), part.y());
	}

	// ---- the passes that turn flat faces into something with a front and a side to it

	private static Tex shaded(Tex art, List<Part> parts) {
		int[] px = art.pixels();
		for (int y = 0; y < art.height; y++) {
			for (int x = 0; x < art.width; x++) {
				int i = y * art.width + x;
				if (Tex.a(px[i]) == 0) continue;
				double dark = x >= PANEL_W / 2 ? SHADE : 0;
				for (Part part : parts) {
					if (part.sleeve() && inside(part, x, y)) { dark += LIMB_SHADE; break; }
				}
				if (dark > 0) px[i] = darker(px[i], dark);
			}
		}
		return Tex.of(art.width, art.height, px);
	}

	/** The seam where the trousers meet the top: the torso's bottom row of px. */
	private static Tex waisted(Tex art, List<Part> parts) {
		Tex out = art;
		for (Part part : parts) {
			if (part.piece() != Piece.TOP || part.sleeve()) continue;
			for (int x = part.x(); x < part.x() + part.widthPx(); x++) {
				int p = out.get(x, FACE - 1);
				if (Tex.a(p) != 0) out = out.with(x, FACE - 1, darker(p, SEAM));
			}
		}
		return out;
	}

	/** A 1 px dark edge along the silhouette, drawn on the figure's own outermost px so it costs no room. */
	private static Tex outlined(Tex art) {
		int[] px = art.pixels();
		int[] out = px.clone();
		int w = art.width, h = art.height;
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int i = y * w + x;
				if (Tex.a(px[i]) == 0) continue;
				boolean edge = x == 0 || x == w - 1 || y == 0 || y == h - 1
						|| Tex.a(px[i - 1]) == 0 || Tex.a(px[i + 1]) == 0 || Tex.a(px[i - w]) == 0 || Tex.a(px[i + w]) == 0;
				if (edge) out[i] = darker(px[i], OUTLINE);
			}
		}
		return Tex.of(w, h, out);
	}

	/**
	 * A patch layer gives up the px the bare doll's outline owns — the ring around each part — so a
	 * patch that hangs over the edge of a sleeve cannot cut the figure's own edge open.
	 */
	private static Tex keptOffTheOutline(Tex art, List<Part> parts) {
		int[] px = art.pixels();
		for (Part part : parts) {
			for (int y = part.y(); y < part.y() + FACE; y++) {
				for (int x = part.x(); x < part.x() + part.widthPx(); x++) {
					boolean ring = x == part.x() || x == part.x() + part.widthPx() - 1 || y == part.y() || y == part.y() + FACE - 1;
					if (ring) px[y * art.width + x] = 0;
				}
			}
		}
		return Tex.of(art.width, art.height, px);
	}

	private static boolean inside(Part part, int x, int y) {
		return x >= part.x() && x < part.x() + part.widthPx() && y >= part.y() && y < part.y() + FACE;
	}

	/** {@code towards} of the way to black, alpha kept. */
	private static int darker(int argb, double towards) {
		return Tex.a(argb) == 0 ? argb : Tex.mix(argb, 0xFF000000, towards);
	}

	/** The smallest {x, y, w, h} holding every visible pixel, or null if there are none. */
	private static int @Nullable [] bounds(Tex art) {
		int x0 = art.width, y0 = art.height, x1 = -1, y1 = -1;
		for (int y = 0; y < art.height; y++) {
			for (int x = 0; x < art.width; x++) {
				if (Tex.a(art.get(x, y)) == 0) continue;
				x0 = Math.min(x0, x);
				y0 = Math.min(y0, y);
				x1 = Math.max(x1, x);
				y1 = Math.max(y1, y);
			}
		}
		return x1 < 0 ? null : new int[]{x0, y0, x1 - x0 + 1, y1 - y0 + 1};
	}

	// ---- the textures the client draws the garment with

	/** Both halves' cloth on one texture, as the client stacks them: the trousers, then the top. */
	private static Tex cloth(Chapter chapter) {
		String key = "cloth/" + chapter.id;
		Tex cached = CACHE.get(key);
		if (cached != null) return cached;
		// Not computeIfAbsent: reading the two halves puts them in this same map. Each with its right
		// limb's copy put back on the box, which holds the checker for unpatched shaderpacks.
		Tex top = GeneratedAssets.rightLimbRestored(read(Piece.TOP, EquipmentJson.baseTexture(chapter, Piece.TOP, false)), Piece.TOP);
		// A frack has no trousers: nothing under the top.
		Tex bottom = chapter.pieces().contains(Piece.BOTTOM)
				? GeneratedAssets.rightLimbRestored(read(Piece.BOTTOM, EquipmentJson.baseTexture(chapter, Piece.BOTTOM, false)), Piece.BOTTOM)
				: Tex.blank(top.width, top.height);
		Tex cloth = bottom.composite(top);
		CACHE.put(key, cloth);
		return cloth;
	}

	/**
	 * The placement's own layer texture — the one {@link EquipmentJson#layerTextures} names for it,
	 * so the doll and the garment can never disagree about where a patch goes. The seat is two
	 * textures, one per leg.
	 */
	private static Tex placementTexture(Placement placement, Part part) {
		List<String> textures = EquipmentJson.textures(placement);
		String texture = textures.size() == 1 ? textures.getFirst() : textures.get(part.side() == Spot.Side.RIGHT ? 0 : 1);
		return read(placement.piece(), Ovvar.MOD_ID + ":" + texture);
	}

	private static Tex read(Piece piece, String texture) {
		String path = "assets/" + Ovvar.MOD_ID + "/textures/entity/equipment/" + piece.layer + "/"
				+ texture.substring(texture.indexOf(':') + 1) + ".png";
		return CACHE.computeIfAbsent(path, p -> {
			try (InputStream in = WardrobePreview.class.getResourceAsStream("/" + p)) {
				if (in == null) throw new IOException("missing " + p + " — run ./gradlew runDatagen");
				return Tex.read(in);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		});
	}
}
