package metacraft.ovvar.pack;

import metacraft.ovvar.content.Chapter;
import metacraft.ovvar.content.Patches;
import metacraft.ovvar.content.Placement;
import metacraft.ovvar.content.Spot;
import metacraft.ovvar.datagen.Tex;
import metacraft.ovvar.pack.WardrobePreview.Angle;
import metacraft.ovvar.sewing.WardrobeGui;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A dev tool, not part of the game: the wardrobe screen as a client would draw it — the chapter
 * background, the paper doll composed exactly as the title's glyphs compose it (the bare ovve for
 * an angle, then one glyph per sewn placement, each at its own place), the empty-state notices, and
 * a marker per slot the screen fills — upscaled so it can be eyeballed without starting a client.
 * Below the screen, the same design from all four angles side by side.
 *
 * <p>{@code ./gradlew :mods:ovvar:wardrobeSheet --offline} writes it; {@code -Psheet=<path>},
 * {@code -Pchapter=<id>} and {@code -PsheetState=empty} pick what it draws. The same one-off spirit
 * as {@code tools/wardrobe_template.py}.
 *
 * <p>What it cannot show, being a compositor and not a client: that the client's own font renderer
 * agrees about the advances and the (negative) ascents, and that nothing of the screen's own chrome
 * lands on top of the doll.
 */
public final class WardrobeSheet {
	private WardrobeSheet() {}

	/** Slot geometry of a {@code GENERIC_9x6} screen, in container px. */
	private static final int SLOT0_X = 8, SLOT0_Y = 18, PITCH = 18, SLOT = 16;
	private static final int UPSCALE = 3;
	/** The marker colours: a filled slot the screen sets, and the preview panel's hover-only slots. */
	private static final int ITEM = 0x60FFFFFF, HOVER = 0x40FFD700;

	public static void main(String[] args) throws IOException {
		Path out = Path.of(args.length > 0 ? args[0] : "/tmp/wardrobe_v3_sheet.png");
		Chapter chapter = args.length > 1 ? Chapter.byId(args[1]) : Chapter.DATA;
		boolean empty = args.length > 2 && args[2].equals("empty");
		if (args.length > 2 && args[2].equals("audit")) {
			audit();
			return;
		}
		// A sample of the catalogue, spread over cells the four angles show between them — an oversize
		// patch (the ITK hangs over its cell), a cell-sized one, and the seat patch, which only the
		// seat takes (Patch.fits): a cell a patch does not fit has no texture and no glyph for it.
		Patches.Patch itk = Patches.get("itk"), nyckeln = Patches.get("nyckeln"), rivals = Patches.get("rivals");
		List<Placement> sewn = empty ? List.of() : List.of(
				new Placement(Spot.FRONT_TOP_LEFT, itk),
				new Placement(Spot.FRONT_LOW_RIGHT, nyckeln),
				new Placement(Spot.SLEEVE_FRONT_TOP_R, nyckeln),
				new Placement(Spot.SLEEVE_OUT_MID_R, itk),
				new Placement(Spot.SHOULDER_R, nyckeln),
				new Placement(Spot.SHOULDER_L, itk),
				// The back's three cells at once, which is what overlapping patches look like: the big
				// one under, the two top cells over it (Spot.layer).
				new Placement(Spot.BACK_BIG, itk),
				new Placement(Spot.BACK_TOP_LEFT, nyckeln),
				new Placement(Spot.BACK_TOP_RIGHT, nyckeln),
				new Placement(Spot.LEG_FRONT_TOP_R, itk),
				new Placement(Spot.LEG_OUT_MID_L, nyckeln),
				new Placement(Spot.SEAT, rivals));

		// The screen itself, with the preview at the angle the screen opens on.
		Tex screen = tex(WardrobeArt.tint(WardrobeArt.readTemplate(), WardrobeArt.colour(chapter)));
		screen = preview(screen, chapter, Angle.FRONT, sewn, 0, 0);
		if (empty) {
			for (WardrobeFont.Glyph notice : List.of(WardrobeFont.NO_PATCHES, WardrobeFont.NOTHING_SEWN)) {
				screen = draw(screen, notice.art().get(), notice.x(), notice.top());
			}
		}
		// The header: the stats readout exactly where the title's glyphs put it, for a sample of
		// counts. (The title's own text is the client's vanilla font, not ours to draw here.)
		List<WardrobeFont.Glyph> stats = WardrobeFont.statsRow(32, sewn.size(), 3, WardrobeGui.titleText(chapter).length());
		for (int i = 0; i < stats.size(); i++) {
			WardrobeFont.Glyph glyph = stats.get(i);
			screen = draw(screen, glyph.art().get(), WardrobeFont.statsX(stats, i), glyph.top());
		}
		// And the pocket's page counter, as a stash of more than twenty kinds would show it.
		List<WardrobeFont.Glyph> counter = WardrobeFont.pagesRow(1, 3);
		for (int i = 0; i < counter.size(); i++) {
			WardrobeFont.Glyph glyph = counter.get(i);
			screen = draw(screen, glyph.art().get(), WardrobeFont.rowX(counter, i, WardrobeFont.PAGE_X), glyph.top());
		}
		for (int[] slot : slots()) screen = box(screen, SLOT0_X + slot[1] * PITCH, SLOT0_Y + slot[0] * PITCH, slot[2]);

		// And the four angles in a row underneath, on the panel's own cloth, at panel size.
		int panel = WardrobePreview.PANEL_W, gap = 4;
		Tex strip = Tex.blank(Angle.values().length * (panel + gap) + gap, panel + 2 * gap);
		Tex cloth = tex(WardrobeArt.tint(WardrobeArt.readTemplate(), WardrobeArt.colour(chapter)))
				.crop(WardrobePreview.PANEL_X, WardrobePreview.PANEL_Y, panel, panel);
		int x = gap;
		for (Angle angle : Angle.values()) {
			strip = strip.blit(cloth, 0, 0, panel, panel, x, gap);
			Tex doll = Tex.blank(panel, panel);
			for (WardrobeFont.Glyph glyph : composed(chapter, angle, sewn)) {
				doll = draw(doll, glyph.art().get(), glyph.x() - WardrobePreview.PANEL_X, glyph.top() - WardrobePreview.PANEL_Y);
			}
			strip = strip.composite(Tex.blank(strip.width, strip.height).blit(doll, 0, 0, panel, panel, x, gap));
			x += panel + gap;
		}

		Tex sheet = Tex.blank(Math.max(screen.width, strip.width), screen.height + strip.height)
				.blit(screen, 0, 0, screen.width, screen.height, 0, 0)
				.blit(strip, 0, 0, strip.width, strip.height, 0, screen.height);
		Files.createDirectories(out.toAbsolutePath().getParent());
		Files.write(out, sheet.scale(UPSCALE).png());
		System.out.println("wrote " + out + " (" + sheet.width * UPSCALE + "x" + sheet.height * UPSCALE + ", "
				+ chapter.id + ", " + sewn.size() + " patches sewn, " + WardrobePreview.glyphCount() + " preview glyphs)");
		// Each sample placement's glyph, with its size and where it goes: a glyph much bigger than a
		// cell at screen scale (4 skin px, so 12 px) would mean a face crop is picking up art that
		// wrapped round a corner onto the face next to it.
		for (Placement placement : sewn) {
			for (Angle angle : WardrobePreview.anglesOf(placement.spot())) {
				WardrobeFont.Glyph glyph = WardrobePreview.patchGlyph(placement, angle);
				if (glyph != null) {
					System.out.println("  " + placement.key() + " (" + angle + "): "
							+ glyph.width() + "x" + glyph.height() + " at (" + glyph.x() + "," + glyph.top() + ")");
				}
			}
		}
	}

	/**
	 * {@code -PsheetState=audit}: every cell's glyph against the patch art it is meant to be
	 * showing (the part of it that lands on the cell's own face — {@link WardrobePreview#shownArt}),
	 * by chromaticity — what a colour still has in common with itself once the doll has shaded it. A
	 * cell whose glyph is missing one of those colours, or has one the art never had, is a crop
	 * landing on cloth beside the patch instead of on the patch. The
	 * {@code wardrobePreviewDrawsEveryCellsOwnPatchArt} game test asserts the same thing; this
	 * prints the whole table at once, which is what a bug report about one cell needs.
	 */
	private static void audit() {
		for (Patches.Patch patch : Patches.all()) {
			System.out.println("== " + patch.id() + " " + patch.variants());
			for (Spot spot : Spot.values()) {
				if (!patch.fits(spot)) continue;
				// The PNG this cell shows, which need not be the catalogue's own size (Patches.artFor).
				Patches.Art chosen = Patches.artFor(patch, spot);
				Tex art = Tex.art(chosen);
				for (Angle angle : WardrobePreview.anglesOf(spot)) {
					WardrobeFont.Glyph glyph = WardrobePreview.patchGlyph(new Placement(spot, patch), angle);
					if (glyph == null) continue;
					// Against the art this cell can show: an oversize patch's hang-over is wrapped round the
					// box by datagen and drawn on the face next door, so it is no part of this cell's picture.
					java.util.List<Integer> want = chromas(WardrobePreview.shownArt(spot, chosen, art, angle));
					java.util.List<Integer> got = chromas(glyph.art().get());
					// To within a level of quantisation: shading multiplies the channels and rounds, and a
					// sleeve is shaded twice over, which can carry a ratio over a bucket boundary.
					java.util.List<Integer> missing = new java.util.ArrayList<>(want.stream().filter(c -> !near(got, c)).toList());
					java.util.List<Integer> extra = new java.util.ArrayList<>(got.stream().filter(c -> !near(want, c)).toList());
					if (!missing.isEmpty() || !extra.isEmpty()) {
						System.out.println("  BAD " + spot.id() + " (" + angle + ") missing " + missing + " extra " + extra);
					}
				}
			}
		}
	}

	private static boolean near(java.util.List<Integer> colours, int colour) {
		for (int other : colours) {
			if (Math.abs((other >> 8 & 0xF) - (colour >> 8 & 0xF)) <= 1
					&& Math.abs((other >> 4 & 0xF) - (colour >> 4 & 0xF)) <= 1
					&& Math.abs((other & 0xF) - (colour & 0xF)) <= 1) return true;
		}
		return false;
	}

	private static java.util.List<Integer> chromas(Tex tex) {
		java.util.List<Integer> out = new java.util.ArrayList<>();
		for (int y = 0; y < tex.height; y++) {
			for (int x = 0; x < tex.width; x++) {
				if (Tex.a(tex.get(x, y)) == 0) continue;
				int p = tex.get(x, y);
				int r = Tex.r(p), g = Tex.g(p), b = Tex.b(p);
				int max = Math.max(r, Math.max(g, b));
				int chroma = max == 0 ? 0 : (r * 15 / max) << 8 | (g * 15 / max) << 4 | b * 15 / max;
				if (!out.contains(chroma)) out.add(chroma);
			}
		}
		return out;
	}

	/** The glyphs the title would carry for this design at this angle, in the order it carries them. */
	private static List<WardrobeFont.Glyph> composed(Chapter chapter, Angle angle, List<Placement> sewn) {
		List<WardrobeFont.Glyph> out = new ArrayList<>();
		out.add(WardrobePreview.bareGlyph(chapter, angle));
		// In the stack's own order, as the title composes them (WardrobePreview.glyphs).
		for (Placement placement : Spot.stacked(sewn)) {
			WardrobeFont.Glyph glyph = WardrobePreview.patchGlyph(placement, angle);
			if (glyph != null) out.add(glyph);
		}
		return out;
	}

	private static Tex preview(Tex sheet, Chapter chapter, Angle angle, List<Placement> sewn, int dx, int dy) {
		Tex out = sheet;
		for (WardrobeFont.Glyph glyph : composed(chapter, angle, sewn)) {
			out = draw(out, glyph.art().get(), glyph.x() + dx, glyph.top() + dy);
		}
		return out;
	}

	/** Every slot the screen fills, as {row, col, colour}: the doll must stay readable between them. */
	private static List<int[]> slots() {
		List<int[]> out = new ArrayList<>();
		for (int col = 0; col < 3; col++) out.add(new int[]{0, col, ITEM});              // chapter tabs
		out.add(new int[]{0, 7, ITEM});                                                   // turn it left
		out.add(new int[]{0, 8, ITEM});                                                   // turn it right
		for (int i = 0; i < 3; i++) out.add(new int[]{1 + i / 5, i % 5, ITEM});           // the patch collection
		for (int col = 5; col < 9; col++) for (int row = 1; row < 5; row++) out.add(new int[]{row, col, HOVER});
		for (int col : new int[]{0, 1, 2, 3, 7, 8}) out.add(new int[]{5, col, ITEM});      // the action row
		return out;
	}

	private static Tex tex(BufferedImage image) {
		int w = image.getWidth(), h = image.getHeight();
		return Tex.of(w, h, image.getRGB(0, 0, w, h, null, 0, w));
	}

	/** {@code top} composited over {@code under} at (x, y), clipped to it. */
	private static Tex draw(Tex under, Tex top, int x, int y) {
		int w = Math.min(top.width, under.width - x), h = Math.min(top.height, under.height - y);
		if (w <= 0 || h <= 0) return under;
		return under.composite(Tex.blank(under.width, under.height).blit(top, 0, 0, w, h, x, y));
	}

	private static Tex box(Tex sheet, int x, int y, int argb) {
		Tex mark = Tex.blank(sheet.width, sheet.height);
		for (int i = 0; i < SLOT; i++) {
			mark = mark.with(x + i, y, argb).with(x + i, y + SLOT - 1, argb).with(x, y + i, argb).with(x + SLOT - 1, y + i, argb);
		}
		return sheet.composite(mark);
	}
}
