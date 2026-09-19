package metacraft.ovvar.sewing;

import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.elements.GuiElement;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import metacraft.ovvar.OvvarConfig;
import metacraft.ovvar.content.Chapter;
import metacraft.ovvar.content.ModContent;
import metacraft.ovvar.content.OvveItem;
import metacraft.ovvar.content.Patches;
import metacraft.ovvar.content.Piece;
import metacraft.ovvar.content.Placement;
import metacraft.ovvar.content.Spot;
import metacraft.ovvar.content.SpotPlacements;
import metacraft.ovvar.pack.WardrobeArt;
import metacraft.ovvar.pack.WardrobeFont;
import metacraft.ovvar.pack.WardrobePreview;
import metacraft.ovvar.pack.WardrobePreview.Angle;
import metacraft.ovvar.store.OwnedSewing;
import metacraft.ovvar.store.Stash;
import metacraft.ovvar.store.StashConfig;
import metacraft.ovvar.store.Wardrobe;
import metacraft.ovvar.store.Wardrobes;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code /ovvar stash} (replaces the old plain-chest StashGui): a font-drawn "swag-i-skogen" wardrobe, one
 * {@code GENERIC_9x6} chest whose background is the player's chapter tinted onto
 * {@link WardrobeArt}'s template and drawn as the container title in the {@code ovvar:wardrobe}
 * font, the same negative-space-plus-bitmap-glyph trick as the sewing dialog
 * ({@link SewingFont}) and better-pets' {@code pet_gui}.
 *
 * <p>Grid ({@code slot = row * 9 + col}):
 * <ul>
 *   <li>row 0: one tab per chapter the player owns an ovve of — the ovve itself, named, the one
 *	   being shown glinting and saying "(showing)" — and at the far right (col
 *	   {@value #PIECE_TOGGLE_COL}) a single toggle naming the half on show and the half a click
 *	   brings up ("Showing: Top — click for Trousers"), not an unexplained chestplate and boots;</li>
 *   <li>rows 1-4, cols 0-4: the patch collection, one slot per stashed patch kind, left/right
 *	   click exactly as the old StashGui did (take out / start a sewing session);</li>
 *   <li>rows 1-4, cols 5-8: the preview — a picture of the player's own garment with their patches
 *	   on it, rendered into the pack by {@link WardrobePreview} and drawn by the title's second
 *	   glyph, with a hover-only item wearing the {@code ovvar:invisible} model over every placement
 *	   this angle shows, at the slot the figure draws that cell in ({@link #previewSlot}) so the
 *	   picture shows through and only the "which patch is where" tooltip is left;</li>
 *   <li>row 5: one verb per action, each with a line saying what it does — take out (col 0),
 *	   put held patches in (col 1), sew on a stand (col 2), see it in 3D (col 3), finish sewing
 *	   (col 4, only mid-session), help (col 7), close (col 8). Cols 0 and 2 are reminders for the
 *	   collection slots' own take-out/sew gesture (there is no "selected patch" for a button to act
 *	   on); an action this server refuses is a grey pane named "… (not here)" carrying the
 *	   reason from {@link StashConfig}, never a slot that is simply missing.</li>
 * </ul>
 * Empty states say what would be there: no patches puts "No patches yet" in the collection's
 * middle, nothing sewn on this half puts "Nothing sewn on yet" in the middle of the bare garment.
 * On a minigame server the screen is look-only, and every refused action says so where it is; the
 * help item (col 7) explains the screen top to bottom in five lines.
 */
public final class WardrobeGui extends SimpleGui {
	private static final int ROWS = 6, WIDTH = 9;
	private static final int TAB_ROW = 0, BODY_TOP = 1, BODY_ROWS = 4;
	/** The row of buttons along the bottom; public because the tests hold the title's glyphs off it. */
	public static final int ACTION_ROW = 5;
	private static final int PATCH_COL0 = 0, PATCH_COLS = 5;
	/** The pocket's slots, and the two of them the page arrows take when there is more than one page. */
	public static final int POCKET = BODY_ROWS * PATCH_COLS;
	public static final int PAGE_PREVIOUS = slot(BODY_TOP + BODY_ROWS - 1, PATCH_COL0);
	public static final int PAGE_NEXT = slot(BODY_TOP + BODY_ROWS - 1, PATCH_COL0 + PATCH_COLS - 1);
	private static final int PREVIEW_COL0 = 5, PREVIEW_COLS = 4;
	private static final int TAKE_OUT_HINT = slot(ACTION_ROW, 0), DEPOSIT = slot(ACTION_ROW, 1), SEW_HINT = slot(ACTION_ROW, 2);
	private static final int MANNEQUIN = slot(ACTION_ROW, 3), FINISH_SEWING = slot(ACTION_ROW, 4);
	private static final int HELP = slot(ACTION_ROW, 7), CLOSE = slot(ACTION_ROW, 8);
	/** How many columns of the tab row the chapter tabs may use; the far end turns the preview. */
	public static final int TAB_COLS = 7;
	public static final int ROTATE_LEFT_COL = 7, ROTATE_RIGHT_COL = 8;
	public static final int ROTATE_LEFT = slot(TAB_ROW, ROTATE_LEFT_COL), ROTATE_RIGHT = slot(TAB_ROW, ROTATE_RIGHT_COL);

	private final Chapter chapter;
	/** Which way round the preview is turned; per open screen, front to begin with. */
	private final Angle angle;
	/** Which page of the pocket is on show; per open screen, the first to begin with. */
	private final int page;
	/** Whose wardrobe is on show — the player's own, or somebody else's for {@code /ovvar look}. */
	private final UUID owner;
	private final String ownerName;

	public static void open(ServerPlayer player) {
		WardrobeGui gui = new WardrobeGui(player, player.getUUID(), player.getName().getString(), defaultChapter(player), Angle.FRONT, 0);
		gui.build();
		gui.open();
	}

	/**
	 * {@code /ovvar look <player>}: the same screen, read-only, showing somebody else's ovve — their
	 * chapters, their design, the preview turning as usual — and none of their stash, which is
	 * theirs. Works for a player who is not here: a wardrobe is a row in a store, not an inventory.
	 */
	public static void look(ServerPlayer viewer, UUID owner, String ownerName) {
		Chapter chapter = Chapter.values()[0];
		if (Wardrobes.loaded(owner)) {
			Wardrobe wardrobe = Wardrobes.current(owner);
			for (Chapter tab : Chapter.values()) {
				if (wardrobe.design(tab).isPresent()) {
					chapter = tab;
					break;
				}
			}
		}
		WardrobeGui gui = new WardrobeGui(viewer, owner, ownerName, chapter, Angle.FRONT, 0);
		gui.build();
		gui.open();
	}

	private WardrobeGui(ServerPlayer player, UUID owner, String ownerName, Chapter chapter, Angle angle, int page) {
		super(MenuType.GENERIC_9x6, player, false);
		this.owner = owner;
		this.ownerName = ownerName;
		this.chapter = chapter;
		this.angle = angle;
		this.page = page;
	}

	/** Is this the player's own wardrobe, or a look at somebody else's? */
	public boolean own() {
		return owner.equals(player.getUUID());
	}

	/**
	 * Built (slots filled, title set) but never {@link #open() opened} on the player's screen —
	 * for gametests to inspect {@link #getGuiElement} without the networking an open screen needs.
	 */
	public static WardrobeGui forTest(ServerPlayer player, Chapter chapter, Angle angle) {
		return forTest(player, chapter, angle, 0);
	}

	public static WardrobeGui forTest(ServerPlayer player, Chapter chapter, Angle angle, int page) {
		WardrobeGui gui = new WardrobeGui(player, player.getUUID(), player.getName().getString(), chapter, angle, page);
		gui.build();
		return gui;
	}

	/** The same, for a look at somebody else's wardrobe. */
	public static WardrobeGui forTestLook(ServerPlayer viewer, UUID owner, String ownerName, Chapter chapter, Angle angle) {
		WardrobeGui gui = new WardrobeGui(viewer, owner, ownerName, chapter, angle, 0);
		gui.build();
		return gui;
	}

	/** Which way the preview is turned right now. */
	public Angle angle() {
		return angle;
	}

	private static int slot(int row, int col) {
		return row * WIDTH + col;
	}

	private static StashConfig config() {
		return OvvarConfig.get().stash();
	}

	// ---- tabs: which chapters the player owns an ovve of

	/** Does the player own (not merely hold) an ovve of this chapter — the tab-visibility rule. */
	public static boolean ownsChapter(ServerPlayer player, Chapter chapter) {
		OvveItem item = ModContent.ovve(chapter);
		return player.getInventory().contains(stack -> stack.getItem() == item && player.getUUID().equals(OvveItem.owner(stack)));
	}

	public static List<Chapter> ownedChapters(ServerPlayer player) {
		List<Chapter> out = new ArrayList<>();
		for (Chapter chapter : Chapter.values()) if (ownsChapter(player, chapter)) out.add(chapter);
		return out;
	}

	private static Chapter defaultChapter(ServerPlayer player) {
		List<Chapter> owned = ownedChapters(player);
		return owned.isEmpty() ? Chapter.values()[0] : owned.get(0);
	}

	// ---- preview: spot -> the slot of the 4x4 preview grid the doll draws that cell in

	/**
	 * Which of the 4x4 hover-only slots over the preview a cell's tooltip belongs on, from the angle
	 * the figure is turned to — or -1 when this angle does not show the cell at all (a cell is on one
	 * face of one box, and a face is seen from one side, so three angles out of four say -1).
	 *
	 * <p>There is no table of spots and cells to keep: the panel is exactly the 4x4 block of 18 px
	 * slot cells at rows 1-4, cols 5-8, so the slot is simply the one holding the centre of the
	 * rectangle {@link WardrobePreview#cellRect} measures the patch into — the compositor's own
	 * geometry, which is what makes the tooltip land on the part of the picture it is about from every
	 * side. Two cells of one angle may still share a slot (16 slots, and a sleeve is 12 px wide): as
	 * before, the last placement drawn to a slot is the one whose tooltip shows, so a design with one
	 * cell per slot — the common case — always reads correctly, and a denser one still points at every
	 * slot it touches.
	 */
	public static int previewSlot(Angle angle, Spot spot) {
		int[] rect = WardrobePreview.cellRect(angle, spot);
		if (rect == null) return -1;
		int col = within((rect[0] + rect[2] / 2) / WardrobeFont.PITCH, PREVIEW_COLS);
		int row = within((rect[1] + rect[3] / 2) / WardrobeFont.PITCH, BODY_ROWS);
		return slot(BODY_TOP + row, PREVIEW_COL0 + col);
	}

	/** The front view's slot for a cell — the angle the screen opens on, and what most callers mean. */
	public static int previewSlot(Spot spot) {
		return previewSlot(Angle.FRONT, spot);
	}

	/** A patch may hang a pixel over the edge of the figure; its centre never leaves the panel. */
	private static int within(int index, int count) {
		return Math.max(0, Math.min(count - 1, index));
	}

	// ---- the title: the chapter's background, then the stats strip

	/** {@code earned = stash + every sewn placement} (from the wardrobe record; there is no separate history). */
	public static int earned(Wardrobe wardrobe) {
		int sewn = 0;
		for (SpotPlacements placements : wardrobe.designs().values()) sewn += placements.asPlacementList().size();
		return wardrobe.stashSize() + sewn;
	}

	/** Everything sewn on this chapter's garment — the preview shows the whole of it, so the stat counts the whole of it. */
	public static int sewnCount(Wardrobe wardrobe, Chapter chapter) {
		return shownPlacements(wardrobe, chapter).size();
	}

	/** The placements the preview draws: this chapter's, both halves, in sewing order. */
	public static List<Placement> shownPlacements(Wardrobe wardrobe, Chapter chapter) {
		return wardrobe.design(chapter).map(SpotPlacements::asPlacementList).orElse(List.of());
	}

	/**
	 * The title for a player: their own pack's generation decides which previews they can be shown,
	 * and {@code activeTab} is the column of the tab they are on (owned-chapter order, so it is
	 * theirs alone) — the highlight under it cannot be baked into a per-chapter background.
	 */
	public static Component title(Chapter chapter, Wardrobe wardrobe, Angle angle, int activeTab, boolean own, int page) {
		MutableComponent text = Component.literal(titleText(chapter)).withStyle(ChatFormatting.WHITE);
		MutableComponent out = Component.empty().append(WardrobeArt.backgroundGlyph(chapter));
		if (activeTab >= 0 && activeTab < TAB_COLS) {
			out.append(WardrobeFont.drawnAt(WardrobeFont.ACTIVE_TAB, WardrobeFont.cellX(activeTab)));
		}
		List<Placement> sewn = shownPlacements(wardrobe, chapter);
		out.append(WardrobePreview.glyphs(chapter, angle, sewn));
		// The empty states are art across the panel they are about, not an item in the middle of it.
		if (!own) out.append(WardrobeFont.drawn(WardrobeFont.LOOK_ONLY));
		else if (wardrobe.stashed().isEmpty()) out.append(WardrobeFont.drawn(WardrobeFont.NO_PATCHES));
		if (sewn.isEmpty()) out.append(WardrobeFont.drawn(WardrobeFont.NOTHING_SEWN));
		// "page 2/3" under the pocket, when the stash has more kinds than one page of it holds.
		out.append(WardrobeFont.pages(page, pageCount(wardrobe.stashed().size())));
		// The counts as pixel text in the spare header width, not as more of the title's own text:
		// at 6 px a character that ran off the right of the screen.
		out.append(WardrobeFont.stats(earned(wardrobe), sewnCount(wardrobe, chapter), wardrobe.stashSize(), titleText(chapter).length()));
		return out.append(text);
	}

	/**
	 * The only text in the title: whose ovve this is. The client draws it in the vanilla font at
	 * about {@value WardrobeFont#TITLE_CHAR} px a character, and the screen is 176 px wide, so
	 * everything else the title used to say is drawn as glyphs instead.
	 */
	public static String titleText(Chapter chapter) {
		String full = " " + chapter.name + " " + chapter.garmentWord();
		// A long chapter name gives up the garment word rather than the counts beside it. Measured
		// against counts nobody will reach, so the title does not wobble as the numbers grow.
		if (!WardrobeFont.statsRow(999, 99, 99, full.length()).isEmpty()) return full;
		return " " + chapter.name;
	}

	/** What the longest chapter name leaves for the stats: the title's text must fit the screen on its own. */
	public static int titleChars() {
		int longest = 0;
		for (Chapter chapter : Chapter.values()) longest = Math.max(longest, titleText(chapter).length());
		return longest;
	}

	/**
	 * {@code [background glyph][active tab highlight][preview glyph][stats text]}, per
	 * {@code docs/superpowers/specs/2026-09-12-ovvar-wardrobe-screen-design.md} §3 and the paper
	 * doll {@link WardrobePreview} adds to it. Every glyph leaves the cursor where it found it, so
	 * the stats strip reads as ordinary text on the same line.
	 */
	public static Component title(Chapter chapter, Wardrobe wardrobe, Angle angle) {
		return title(chapter, wardrobe, angle, 0, true, 0);
	}

	// ---- building the screen

	private void build() {
		for (int i = 0; i < ROWS * WIDTH; i++) clearSlot(i);
		if (!Wardrobes.loaded(owner)) {
			Wardrobes.fetch(owner);
			setTitle(WardrobeArt.backgroundGlyph(chapter));
			setSlot(slot(BODY_TOP + 1, PATCH_COL0 + 2), new GuiElementBuilder(Items.CLOCK)
					.setName(Component.literal("Loading " + (own() ? "your wardrobe…" : ownerName + "'s wardrobe…")).withStyle(ChatFormatting.YELLOW))
					.addLoreLine(Component.literal("Close and open again in a moment").withStyle(ChatFormatting.GRAY)).build());
			setSlot(CLOSE, closeButton());
			return;
		}
		Wardrobe wardrobe = Wardrobes.current(owner);
		List<Chapter> tabs = tabs(wardrobe);
		setTitle(title(chapter, wardrobe, angle, tabs.indexOf(chapter), own(), page));

		buildTabs(tabs);
		buildPreview(wardrobe);
		if (own()) {
			buildCollection(player, wardrobe);
			buildActions(player, wardrobe);
		} else {
			// A look and nothing more: no take out, no put in, no sewing, no mannequin.
			setSlot(HELP, help(wardrobe));
			setSlot(CLOSE, closeButton());
		}
	}

	/**
	 * The chapters this screen has tabs for: the ones the player owns an ovve of, or — for a look at
	 * somebody else, whose inventory is none of our business and may not even be loaded — the ones
	 * they have a design for.
	 */
	private List<Chapter> tabs(Wardrobe wardrobe) {
		if (own()) return ownedChapters(player);
		List<Chapter> out = new ArrayList<>();
		for (Chapter tab : Chapter.values()) if (wardrobe.design(tab).isPresent()) out.add(tab);
		return out;
	}

	private void buildTabs(List<Chapter> tabs) {
		int col = 0;
		for (Chapter tab : tabs) {
			boolean current = tab == chapter;
			GuiElementBuilder element = GuiElementBuilder.from(new ItemStack(ModContent.ovve(tab)))
					.setName(Component.literal(tab.name + " " + tab.garmentWord()).withStyle(current ? ChatFormatting.GOLD : ChatFormatting.WHITE))
					.addLoreLine(Component.literal(current ? "(showing)" : "Click to switch to it").withStyle(current ? ChatFormatting.GOLD : ChatFormatting.GRAY))
					.glow(current);
			element.setCallback((index, type, action, gui) -> reopen(tab, angle));
			setSlot(slot(TAB_ROW, col++), element.build());
			if (col >= TAB_COLS) break;   // leave the far end to the piece toggle
		}
		setSlot(ROTATE_LEFT, rotator(-1, WardrobeAction.ROTATE_LEFT));
		setSlot(ROTATE_RIGHT, rotator(1, WardrobeAction.ROTATE_RIGHT));
	}

	/**
	 * Opens the screen again on another tab or at another angle. A container's title only travels in
	 * the packet that opens it, and the whole preview is drawn by the title, so turning the figure is
	 * re-opening the screen — which is also how switching tabs has always worked here.
	 */
	private void reopen(Chapter tab, Angle to) {
		reopen(tab, to, page);
	}

	private void reopen(Chapter tab, Angle to, int toPage) {
		if (!isOpen()) return;
		WardrobeGui next = new WardrobeGui(player, owner, ownerName, tab, to, toPage);
		next.build();
		next.open();
	}

	/**
	 * One of the two buttons that turn the preview, at the end of the tab row. Four sides, one step at
	 * a time, and the button names the side it would bring round. The ovve is one figure now, top and
	 * trousers together, so there is nothing left to toggle between — which is what the piece toggle
	 * that used to sit here was for.
	 */
	private GuiElement rotator(int turn, WardrobeAction what) {
		Angle to = angle.turned(turn);
		return GuiElementBuilder.from(icon(what, true))
				.setName(Component.literal(what.verb).withStyle(ChatFormatting.AQUA))
				.addLoreLine(Component.literal("Showing: " + angle.label).withStyle(ChatFormatting.GRAY))
				.addLoreLine(Component.literal("Click for: " + to.label).withStyle(ChatFormatting.DARK_GRAY))
				.setCallback((index, type, action, gui) -> reopen(chapter, to)).build();
	}

	/**
	 * How many kinds of patch one page of the pocket holds. All twenty of its slots while they are
	 * enough; eighteen once they are not, because the two ends of the bottom row go to the page
	 * arrows — both of them, on every page, even the pages that only need one, so that the arrows
	 * never move under the pointer.
	 */
	public static int perPage(int kinds) {
		return kinds > POCKET ? POCKET - 2 : POCKET;
	}

	/** How many pages the pocket needs for {@code kinds} kinds of patch; never fewer than one. */
	public static int pageCount(int kinds) {
		int per = perPage(kinds);
		return Math.max(1, (kinds + per - 1) / per);
	}

	/** The pocket's slots that hold patches on a screen that is paging, in reading order. */
	public static List<Integer> pocketSlots(boolean paging) {
		List<Integer> out = new ArrayList<>();
		for (int i = 0; i < POCKET; i++) {
			int row = i / PATCH_COLS, col = i % PATCH_COLS;
			boolean arrow = paging && row == BODY_ROWS - 1 && (col == 0 || col == PATCH_COLS - 1);
			if (!arrow) out.add(slot(BODY_TOP + row, PATCH_COL0 + col));
		}
		return out;
	}

	private void buildCollection(ServerPlayer player, Wardrobe wardrobe) {
		String refusal = OwnedSewing.editingRefusal(player);
		boolean canSew = refusal == null && config().sessions(), canTake = refusal == null && config().canWithdraw();
		boolean leftTakes = !config().sessions() || config().stashClick() == StashConfig.StashClick.WITHDRAW;

		// One kind's stack of items, not one item per patch, so "kind" and "collection slot" are the
		// same thing. Sorted by name, so a kind keeps its place from one opening to the next and does
		// not move about as the counts change; a stash with more kinds than the pocket holds is paged.
		List<Patches.Patch> stashed = new ArrayList<>(wardrobe.stashed());
		stashed.sort(java.util.Comparator.comparing(Patches.Patch::name));
		int pages = pageCount(stashed.size());
		int showing = Math.min(page, pages - 1);
		List<Integer> slots = pocketSlots(pages > 1);
		int from = showing * perPage(stashed.size());
		for (int i = 0; i + from < stashed.size() && i < slots.size(); i++) {
			Patches.Patch patch = stashed.get(from + i);
			int count = wardrobe.count(patch);
			GuiElementBuilder element = GuiElementBuilder.from(new ItemStack(ModContent.patchItem(patch), Math.min(count, 64)))
					.setName(Component.literal(patch.name()).withStyle(ChatFormatting.WHITE))
					.addLoreLine(Component.literal(count + " in the stash").withStyle(ChatFormatting.GRAY))
					.addLoreLine(Component.literal(patch.seat() ? "Goes across the seat" : "Goes anywhere on an ovve").withStyle(ChatFormatting.DARK_GRAY));
			// Whoever drew it, credited here as well as on the item itself (PatchItem): the stash is
			// where a player looks at their patches, and the screen that replaced StashGui owes the
			// credit that one carried.
			if (patch.artist() != null) element.addLoreLine(Component.literal("Art by " + patch.artist()).withStyle(ChatFormatting.GRAY));
			String take = "take one out (sew it on a stand, or trade it)", sew = "sew it on your ovve here";
			if (canTake) element.addLoreLine(Component.literal((leftTakes ? "Left" : "Right") + "-click: " + take).withStyle(ChatFormatting.YELLOW));
			if (canSew) element.addLoreLine(Component.literal((leftTakes ? "Right" : "Left") + "-click: " + sew).withStyle(ChatFormatting.YELLOW));
			if (refusal != null) element.addLoreLine(Component.literal(refusal).withStyle(ChatFormatting.RED));
			element.setCallback((index, type, action, gui) -> {
				boolean left = type == ClickType.MOUSE_LEFT, right = type == ClickType.MOUSE_RIGHT;
				boolean wantsTake = leftTakes ? left : right, wantsSew = leftTakes ? right : left;
				if (wantsTake && canTake) {
					Stash.withdraw(player, patch, reply -> {
						player.sendOverlayMessage(Component.literal(reply));
						if (isOpen()) build();
					});
				} else if (wantsSew && canSew) {
					close();
					StashSession.start(player, patch, why -> player.sendSystemMessage(Component.literal(why).withStyle(ChatFormatting.RED)));
				}
			});
			setSlot(slots.get(i), element.build());
		}
		// An empty stash says so in the glyph layer (WardrobeFont.NO_PATCHES, drawn by the title
		// across the whole pocket): no item here, so there is nothing to hover or mistake for a patch.
		if (pages > 1) {
			if (showing > 0) setSlot(PAGE_PREVIOUS, pageArrow(WardrobeAction.PAGE_PREVIOUS, showing - 1, pages));
			if (showing < pages - 1) setSlot(PAGE_NEXT, pageArrow(WardrobeAction.PAGE_NEXT, showing + 1, pages));
		}
	}

	/** One of the pocket's page arrows: the rotation arrows' own sprite, pointing the same way, another job. */
	private GuiElement pageArrow(WardrobeAction what, int to, int pages) {
		return GuiElementBuilder.from(icon(what, true))
				.setName(Component.literal(what.verb).withStyle(ChatFormatting.AQUA))
				.addLoreLine(Component.literal(what.does).withStyle(ChatFormatting.GRAY))
				.addLoreLine(Component.literal("Page " + (to + 1) + " of " + pages).withStyle(ChatFormatting.DARK_GRAY))
				.setCallback((index, type, action, gui) -> reopen(chapter, angle, to)).build();
	}

	/**
	 * Rows 1-4, cols 5-8: the paper doll is the title's second glyph, drawn behind these slots, so
	 * every slot here carries nothing but a tooltip — an item with the {@code ovvar:invisible} model
	 * (a transparent icon), no callback, over each placement the angle on show draws — at the slot
	 * that angle draws the cell in, so the figure can be turned and the tooltips turn with it.
	 */
	private void buildPreview(Wardrobe wardrobe) {
		// Every angle: the tooltip goes where this angle really draws the cell (previewSlot), so a
		// patch on the back is hoverable on the back view and the front view leaves those slots
		// empty. Two cells of one angle that share a slot: the last one drawn wins, as before.
		// Nothing sewn at all says so in the glyph layer (WardrobeFont.NOTHING_SEWN).
		for (Placement placement : shownPlacements(wardrobe, chapter)) {
			int slot = previewSlot(angle, placement.spot());
			if (slot < 0) continue;
			GuiElementBuilder element = GuiElementBuilder.from(invisible())
					.setName(Component.literal(placement.patch().name()).withStyle(ChatFormatting.WHITE))
					.addLoreLine(Component.literal(placement.patch().name() + " on " + placement.spot().label()).withStyle(ChatFormatting.GRAY));
			if (!own()) element.addLoreLine(Component.literal("on " + ownerName + "'s " + chapter.garmentWord()).withStyle(ChatFormatting.DARK_GRAY));
			setSlot(slot, element.build());
		}
	}

	/** A stack that draws nothing: hover and it has a name and lore, look at it and the picture behind shows through. */
	private static ItemStack invisible() {
		ItemStack stack = new ItemStack(Items.PAPER);
		stack.set(DataComponents.ITEM_MODEL, INVISIBLE_MODEL);
		return stack;
	}

	private static final Identifier INVISIBLE_MODEL = ModContent.id("invisible");

	/**
	 * Row 5: one verb per action, each with a line saying what it does. An action this server does
	 * not allow is not missing — a slot that is simply gone teaches nobody anything — it is a grey
	 * pane named "\<verb\> (not here)" carrying the reason {@link StashConfig} gives for it.
	 */
	private void buildActions(ServerPlayer player, Wardrobe wardrobe) {
		setSlot(TAKE_OUT_HINT, action(WardrobeAction.TAKE_OUT, config().whyNoWithdraw(), null));
		setSlot(SEW_HINT, action(WardrobeAction.SEW, config().whyNoSessions(), null));
		setSlot(DEPOSIT, action(WardrobeAction.PUT_IN, config().whyNoDeposit(), () -> Stash.deposit(player, reply -> {
			player.sendOverlayMessage(Component.literal(reply));
			if (isOpen()) build();
		})));

		ItemStack worn = player.getItemBySlot(EquipmentSlot.LEGS);
		String noMannequin = config().whyNoMannequin() != null ? config().whyNoMannequin()
				: worn.getItem() instanceof OvveItem ? null : "Wear an ovve first";
		setSlot(MANNEQUIN, action(WardrobeAction.SEE_3D, noMannequin, () -> {
			ItemStack copy = worn.copy();
			String refusal = WardrobeMannequin.show(player, copy);
			if (refusal != null) player.sendSystemMessage(Component.literal(refusal).withStyle(ChatFormatting.RED));
			close();
		}));

		if (StashSession.of(player) != null) {
			setSlot(FINISH_SEWING, new GuiElementBuilder(Items.BARRIER).setName(Component.literal("Finish sewing").withStyle(ChatFormatting.RED))
					.addLoreLine(Component.literal("Ends the session: the stand goes, your hotbar comes back").withStyle(ChatFormatting.GRAY))
					.setCallback((index, type, action, gui) -> {
						close();
						StashSession.end(player, "Sewing session over");
					}).build());
		}

		setSlot(HELP, help(wardrobe));
		setSlot(CLOSE, closeButton());
	}

	/**
	 * An action item: its own icon, the verb, the one line saying what it does, and a click — or,
	 * when {@code why} says it cannot be done here, the same icon dimmed and slashed, that reason in
	 * red, and no click at all. {@code click} null means the verb is a reminder for a gesture that
	 * lives on the collection slots themselves (there is no selected patch for a button to act on).
	 */
	private GuiElement action(WardrobeAction what, @Nullable String why, @Nullable Runnable click) {
		boolean available = why == null;
		GuiElementBuilder element = GuiElementBuilder.from(icon(what, available))
				.setName(Component.literal(available ? what.verb : what.verb + " (not here)")
						.withStyle(available ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY));
		if (!available) element.addLoreLine(Component.literal(why).withStyle(ChatFormatting.RED));
		element.addLoreLine(Component.literal(what.does).withStyle(available ? ChatFormatting.GRAY : ChatFormatting.DARK_GRAY));
		if (available && click != null) element.setCallback((index, type, action, gui) -> click.run());
		return element.build();
	}

	/** A blank stack wearing one of our own action models: the icon is the model, not the item. */
	private static ItemStack icon(WardrobeAction what, boolean available) {
		ItemStack stack = new ItemStack(Items.PAPER);
		stack.set(DataComponents.ITEM_MODEL, what.model(available));
		return stack;
	}

	/** The screen explained top to bottom, in five lines, then what this server allows and what is sewn where. */
	private GuiElement help(Wardrobe wardrobe) {
		GuiElementBuilder book = new GuiElementBuilder(Items.BOOK)
				.setName(Component.literal(own() ? "What this screen is" : "A look at " + ownerName + "'s " + chapter.garmentWord()).withStyle(ChatFormatting.GOLD));
		if (!own()) {
			book.addLoreLine(Component.literal("Their ovve, their patches, look only.").withStyle(ChatFormatting.YELLOW));
			book.addLoreLine(Component.literal("Top row: an ovve per chapter they have sewn on — click to switch;").withStyle(ChatFormatting.GRAY));
			book.addLoreLine(Component.literal("  the far right turns the figure round.").withStyle(ChatFormatting.GRAY));
			book.addLoreLine(Component.literal("Right panel: their ovve as it looks now — hover a slot to").withStyle(ChatFormatting.GRAY));
			book.addLoreLine(Component.literal("  see which patch is sewn where on it.").withStyle(ChatFormatting.GRAY));
			book.addLoreLine(Component.literal("Their stash is their own and is not shown.").withStyle(ChatFormatting.DARK_GRAY));
			book.addLoreLine(Component.literal("Patches sewn on this " + chapter.garmentWord() + ": " + sewnCount(wardrobe, chapter)).withStyle(ChatFormatting.WHITE));
			return book.build();
		}
		book.addLoreLine(Component.literal("Top row: an ovve per chapter you own — click one to switch;").withStyle(ChatFormatting.GRAY));
		book.addLoreLine(Component.literal("  the far right turns the figure round.").withStyle(ChatFormatting.GRAY));
		book.addLoreLine(Component.literal("Left panel: your stash, one slot per kind of patch you own.").withStyle(ChatFormatting.GRAY));
		book.addLoreLine(Component.literal("Right panel: your own ovve as it looks now — hover a slot to").withStyle(ChatFormatting.GRAY));
		book.addLoreLine(Component.literal("  see which patch is sewn where on it.").withStyle(ChatFormatting.GRAY));
		book.addLoreLine(Component.literal("Bottom row: what you can do here, greyed out where you cannot.").withStyle(ChatFormatting.GRAY));
		if (config().minigameServer()) {
			book.addLoreLine(Component.literal(StashConfig.LOOK_ONLY + " — sew on a survival server").withStyle(ChatFormatting.RED));
		} else {
			boolean leftTakes = !config().sessions() || config().stashClick() == StashConfig.StashClick.WITHDRAW;
			if (config().canWithdraw()) book.addLoreLine(Component.literal((leftTakes ? "Left" : "Right") + "-click a patch to take it out as an item (trade it!)").withStyle(ChatFormatting.WHITE));
			if (config().sessions()) book.addLoreLine(Component.literal((leftTakes ? "Right" : "Left") + "-click to sew it on your ovve on a private stand").withStyle(ChatFormatting.WHITE));
		}
		// The counts in full, whatever the header had room to draw of them.
		book.addLoreLine(Component.literal("Patches earned " + earned(wardrobe) + " · sewn on this ovve " + sewnCount(wardrobe, chapter)
				+ " · in the stash " + wardrobe.stashSize()).withStyle(ChatFormatting.WHITE));
		book.addLoreLine(Component.literal("The stash and your ovvar follow you to every server").withStyle(ChatFormatting.DARK_GRAY));
		for (Map.Entry<Chapter, SpotPlacements> entry : wardrobe.designs().entrySet()) {
			List<Placement> list = entry.getValue().asPlacementList();
			book.addLoreLine(Component.literal(entry.getKey().name + " " + entry.getKey().garmentWord() + ": " + list.size() + " patch(es)").withStyle(ChatFormatting.DARK_GRAY));
		}
		return book.build();
	}

	private GuiElement closeButton() {
		return new GuiElementBuilder(Items.SPRUCE_DOOR).setName(Component.literal("Close").withStyle(ChatFormatting.GRAY))
				.setCallback((index, type, action, gui) -> close()).build();
	}
}
