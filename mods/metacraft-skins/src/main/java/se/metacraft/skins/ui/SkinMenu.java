package se.metacraft.skins.ui;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import se.metacraft.skins.ApplyResult;
import se.metacraft.skins.BoxSummary;
import se.metacraft.skins.Candidate;
import se.metacraft.skins.Fit;
import se.metacraft.skins.OwnedSkin;
import se.metacraft.skins.OwnershipChange;
import se.metacraft.skins.PerkLines;
import se.metacraft.skins.PlayerSkins;
import se.metacraft.skins.Release;
import se.metacraft.skins.SkinFolder;
import se.metacraft.skins.SkinMarker;
import se.metacraft.skins.SkinSummary;
import se.metacraft.skins.Skins;
import se.metacraft.skins.Tint;
import se.metacraft.skins.item.SkinToken;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The Skin Menu ({@code /itemskins}): two chest screens, built with sgui, that only ever call the
 * {@link Skins} module, and a third for {@code /itemskins list}. The player's own inventory below
 * stays visible but locked on all of them.
 * <ul>
 *   <li><b>"Your Skins"</b> (6 rows): rows 1–5 every skin on the server that loaded, 45 a page, in
 *       the menus' {@linkplain #order order} (by Skin Box). The player's own are shown with their Look,
 *       glinting while applied; the rest are a dye named "???" that does nothing when clicked, light
 *       grey ("Not owned yet") or, for an unobtainable skin, grey ("Unobtainable"). Row 6 holds the page
 *       arrows (only with more than one page), the "How skins work" book, which sums skins up in
 *       its tooltip, and the Extract button.</li>
 *   <li><b>"Apply: &lt;skin&gt;"</b> (4 rows), opened by clicking a skin: Back, a preview, Clear tint
 *       for a Dyeable Hat with a saved tint and, while the skin is applied, Remove along row 1; below, every item on the player the skin fits,
 *       hotbar to offhand, 27 a page, each shown as their own inventory shows it and saying where it
 *       is and what clicking it does. With more
 *       than one page, arrows either side of the preview turn them. Clicking an item applies the
 *       skin and goes back to "Your Skins".</li>
 *   <li><b>"Extract: choose a skin"</b> (6 rows), opened by the Extract button: the player's own
 *       skins, laid out and paged as in "Your Skins", with Back in row 6. Clicking a skin opens the
 *       {@link ExtractMenu} for it; a skin on an item says to remove it first and does nothing.
 *       The Extract screen's Cancel, and a finished Extract, come back to "Your Skins".</li>
 *   <li><b>"&lt;Name&gt;'s Skins"</b> ({@code /itemskins <player>}, admins): another player's "Your Skins", to look
 *       at only ({@link #openFor}).</li>
 *   <li><b>"All Skins"</b> ({@code /itemskins list}): "Your Skins", but with every skin's Look, owned
 *       or not, and nothing to click; Dormant skins come last, as barriers saying why.</li>
 * </ul>
 * Every skin is shown with its Look, named in its colours ({@link se.metacraft.skins.SkinName}), and
 * says when it was released and whether it's obtainable ({@link se.metacraft.skins.Release}).
 */
public final class SkinMenu {
	private SkinMenu() {}

	static final int PER_PAGE = 45;
	/** "Your Skins": row 6. "Extract: choose a skin" has its Back where the book is. */
	static final int PREVIOUS_PAGE = 45, BOOK = 49, EXTRACT = 50, NEXT_PAGE = 53;
	/** "Apply: <skin>": row 1, then the items from row 2 on. */
	static final int BACK = 0, PREVIOUS_ITEMS = 3, PREVIEW = 4, NEXT_ITEMS = 5, CLEAR_TINT = 7, REMOVE = 8, FIRST_ITEM = 9, MIDDLE = 22, ITEMS_PER_PAGE = 27;
	/** How a refused Remove is worded. */
	private static final Feedback.Audience REMOVING = new Feedback.Audience.Owner("remove", "nothing changed");
	/** How a refused Clear tint is worded. */
	private static final Feedback.Audience CLEARING = new Feedback.Audience.Owner("clear the tint of", "the tint stays");

	/**
	 * Opens "Your Skins" on its first page, once the player's own items are tidied ({@link Skins#tidy}):
	 * a skin that fell off its item (a smithing upgrade past its Materials) is Unapplied by the time it's listed.
	 */
	public static void open(ServerPlayer player, Skins skins) {
		skins.tidy(player);
		yourSkins(player, skins, 0);
	}

	/** Within a box: by {@code released}, oldest first, then by name and id; skins without a date last. */
	private static final Comparator<SkinSummary> BY_RELEASE = Comparator.comparing((SkinSummary s) -> s.release().flatMap(Release::released).orElse(LocalDate.MAX))
			.thenComparing(SkinSummary::displayName).thenComparing(SkinSummary::id);

	/**
	 * The one order the menus list skins in: by the Skin Box they're in, boxes in id order (so the
	 * Classic Hats Box's skins, then the Winter Skin Box's), a skin in two boxes going with the first;
	 * then the skins in no box. Within each, {@link #BY_RELEASE}.
	 */
	static Comparator<SkinSummary> order(Skins skins) {
		Map<String, Integer> box = new HashMap<>();
		List<BoxSummary> boxes = skins.boxes();
		for (int i = 0; i < boxes.size(); i++) {
			for (BoxSummary.Entry entry : boxes.get(i).entries()) box.putIfAbsent(entry.skin(), i);
		}
		return Comparator.comparingInt((SkinSummary s) -> box.getOrDefault(s.id(), Integer.MAX_VALUE)).thenComparing(BY_RELEASE);
	}

	/** One loaded skin as "Your Skins" lists it, with the player's ownership of it if they own it. */
	private record Listed(SkinSummary skin, Fit fit, Optional<OwnedSkin> owned) {
		boolean applied() {
			return owned.filter(o -> o.state() == OwnedSkin.State.APPLIED).isPresent();
		}

		/** A Dyeable Hat's saved tint. */
		Optional<Tint> tint() {
			return skin.dyeable() ? owned.flatMap(OwnedSkin::tint) : Optional.empty();
		}
	}

	/** Every loaded skin in the menus' {@linkplain #order order}; owned ones carry their ownership. A damaged file lists nothing. */
	private static List<Listed> listed(Skins skins, PlayerSkins owned) {
		if (owned.damage().isPresent()) return List.of();
		Map<String, OwnedSkin> byId = new HashMap<>();
		for (OwnedSkin skin : owned.skins()) if (skin.state() != OwnedSkin.State.DORMANT) byId.put(skin.id(), skin);
		return skins.allSkins().stream().filter(s -> !s.dormant() && s.fit().isPresent()).sorted(order(skins))
				.map(s -> new Listed(s, s.fit().get(), Optional.ofNullable(byId.get(s.id())))).toList();
	}

	private static void yourSkins(ServerPlayer player, Skins skins, int requestedPage) {
		PlayerSkins owned = skins.ownedSkins(player.getUUID());
		List<Listed> listed = listed(skins, owned);
		int pages = Math.max(1, (listed.size() + PER_PAGE - 1) / PER_PAGE);
		int page = Math.clamp(requestedPage, 0, pages - 1);

		SimpleGui gui = locked(MenuType.GENERIC_9x6, player);
		gui.setTitle(Component.literal("Your Skins" + (pages > 1 ? " (" + (page + 1) + "/" + pages + ")" : "")));
		List<Listed> shown = listed.subList(page * PER_PAGE, Math.min(listed.size(), (page + 1) * PER_PAGE));
		for (int i = 0; i < shown.size(); i++) {
			Listed skin = shown.get(i);
			if (skin.owned().isEmpty()) {
				gui.setSlot(i, notOwned(skin.skin()));
				continue;
			}
			gui.setSlot(i, icon(skin).addLoreLine(Component.literal("Click to apply or remove").withStyle(ChatFormatting.DARK_GRAY))
					.setCallback(() -> applyScreen(player, skins, skin.skin().id(), page, 0)).build());
		}
		if (owned.damage().isPresent()) {
			gui.setSlot(MIDDLE, damaged());
		} else if (listed.isEmpty()) {
			gui.setSlot(MIDDLE, new GuiElementBuilder(Items.PAPER).setName(Component.literal("There are no skins on this server yet")).build());
		}
		for (int i = PER_PAGE; i < PER_PAGE + 9; i++) gui.setSlot(i, filler());
		if (page > 0) gui.setSlot(PREVIOUS_PAGE, arrow("Previous page", () -> yourSkins(player, skins, page - 1)));
		if (page < pages - 1) gui.setSlot(NEXT_PAGE, arrow("Next page", () -> yourSkins(player, skins, page + 1)));
		gui.setSlot(BOOK, book());
		gui.setSlot(EXTRACT, new GuiElementBuilder(Items.PAPER).model(SkinToken.MODEL).setName(Component.literal("Extract a skin").withStyle(ChatFormatting.GOLD))
				.addLoreLine(Component.literal("Turn a skin you own back into").withStyle(ChatFormatting.GRAY))
				.addLoreLine(Component.literal("its Skin Token, to trade or give away").withStyle(ChatFormatting.GRAY))
				.setCallback(() -> extractScreen(player, skins, page, 0)).build());
		gui.open();
	}

	/**
	 * "Extract: choose a skin" on page {@code requestedPage}: the player's own skins, each saying
	 * whether it can be extracted ({@link Skins#extractable}); {@code fromPage} is the page of "Your Skins" to go back to.
	 */
	private static void extractScreen(ServerPlayer player, Skins skins, int fromPage, int requestedPage) {
		PlayerSkins owned = skins.ownedSkins(player.getUUID());
		List<Listed> mine = listed(skins, owned).stream().filter(l -> l.owned().isPresent()).toList();
		int pages = Math.max(1, (mine.size() + PER_PAGE - 1) / PER_PAGE);
		int page = Math.clamp(requestedPage, 0, pages - 1);

		SimpleGui gui = locked(MenuType.GENERIC_9x6, player);
		gui.setTitle(Component.literal("Extract: choose a skin" + (pages > 1 ? " (" + (page + 1) + "/" + pages + ")" : "")));
		List<Listed> shown = mine.subList(page * PER_PAGE, Math.min(mine.size(), (page + 1) * PER_PAGE));
		for (int i = 0; i < shown.size(); i++) {
			Listed skin = shown.get(i);
			String id = skin.skin().id();
			OwnershipChange extractable = skins.extractable(player, id);
			GuiElementBuilder icon = icon(skin);
			if (extractable == OwnershipChange.DONE) {
				icon.addLoreLine(Component.literal("Click to extract").withStyle(ChatFormatting.YELLOW)).setCallback(() -> confirmExtract(player, skins, id, fromPage, page));
			} else {
				icon.addLoreLine(Component.literal(extractable == OwnershipChange.STILL_APPLIED ? "Remove it from its item first" : "Can't be extracted right now")
						.withStyle(ChatFormatting.RED));
			}
			gui.setSlot(i, icon.build());
		}
		if (owned.damage().isPresent()) {
			gui.setSlot(MIDDLE, damaged());
		} else if (mine.isEmpty()) {
			gui.setSlot(MIDDLE, new GuiElementBuilder(Items.PAPER).setName(Component.literal("You don't own any skins yet"))
					.addLoreLine(Component.literal("Claim a Skin Token to own one.").withStyle(ChatFormatting.GRAY)).build());
		}
		for (int i = PER_PAGE; i < PER_PAGE + 9; i++) gui.setSlot(i, filler());
		if (page > 0) gui.setSlot(PREVIOUS_PAGE, arrow("Previous page", () -> extractScreen(player, skins, fromPage, page - 1)));
		if (page < pages - 1) gui.setSlot(NEXT_PAGE, arrow("Next page", () -> extractScreen(player, skins, fromPage, page + 1)));
		gui.setSlot(BOOK, arrow("Back to your skins", () -> yourSkins(player, skins, fromPage)));
		gui.open();
	}

	/** A skin clicked in "Extract: choose a skin": checked again, then its Extract screen, which comes back to "Your Skins". */
	private static void confirmExtract(ServerPlayer player, Skins skins, String skinId, int fromPage, int extractPage) {
		OwnershipChange extractable = skins.extractable(player, skinId);
		if (extractable != OwnershipChange.DONE) {
			// Applied, revoked or gone Dormant since the list was drawn.
			Feedback.failed(player, ExtractMenu.whyNot(extractable, skins.skin(skinId).map(SkinSummary::displayName).orElse(skinId)));
			extractScreen(player, skins, fromPage, extractPage);
			return;
		}
		ExtractMenu.open(player, skins, skinId, () -> yourSkins(player, skins, fromPage));
	}

	/**
	 * Opens "&lt;Name&gt;'s Skins" for an admin ({@code /itemskins <player>}): another player's "Your Skins"
	 * as they'd see it, online or not, to look at only. Their own skins show with their Look, tint
	 * and whether they're applied, the rest as "???"; nothing can be clicked, there's no Extract
	 * button, and their items aren't tidied. The book's place says whose skins they are and how many.
	 */
	public static void openFor(ServerPlayer viewer, Skins skins, NameAndId whose) {
		othersSkins(viewer, skins, whose, 0);
	}

	private static void othersSkins(ServerPlayer viewer, Skins skins, NameAndId whose, int requestedPage) {
		PlayerSkins owned = skins.ownedSkins(whose.id());
		List<Listed> listed = listed(skins, owned);
		int pages = Math.max(1, (listed.size() + PER_PAGE - 1) / PER_PAGE);
		int page = Math.clamp(requestedPage, 0, pages - 1);

		SimpleGui gui = locked(MenuType.GENERIC_9x6, viewer);
		gui.setTitle(Component.literal(whose.name() + "'s Skins" + (pages > 1 ? " (" + (page + 1) + "/" + pages + ")" : "")));
		List<Listed> shown = listed.subList(page * PER_PAGE, Math.min(listed.size(), (page + 1) * PER_PAGE));
		for (int i = 0; i < shown.size(); i++) {
			Listed skin = shown.get(i);
			gui.setSlot(i, skin.owned().isEmpty() ? notOwned(skin.skin()) : icon(skin).build());
		}
		if (owned.damage().isPresent()) {
			gui.setSlot(MIDDLE, new GuiElementBuilder(Items.BARRIER).setName(Component.literal(whose.name() + "'s skins can't be shown").withStyle(ChatFormatting.RED))
					.addLoreLine(Component.literal("Their skins file is damaged: /itemskins info " + whose.name()).withStyle(ChatFormatting.GRAY)).build());
		} else if (listed.isEmpty()) {
			gui.setSlot(MIDDLE, new GuiElementBuilder(Items.PAPER).setName(Component.literal("There are no skins on this server yet")).build());
		}
		for (int i = PER_PAGE; i < PER_PAGE + 9; i++) gui.setSlot(i, filler());
		if (page > 0) gui.setSlot(PREVIOUS_PAGE, arrow("Previous page", () -> othersSkins(viewer, skins, whose, page - 1)));
		if (page < pages - 1) gui.setSlot(NEXT_PAGE, arrow("Next page", () -> othersSkins(viewer, skins, whose, page + 1)));
		long ownedCount = listed.stream().filter(l -> l.owned().isPresent()).count();
		gui.setSlot(BOOK, new GuiElementBuilder(Items.PLAYER_HEAD).setName(Component.literal(whose.name() + "'s skins"))
				.addLoreLine(Component.literal("Owns " + ownedCount + " of " + listed.size() + " skins").withStyle(ChatFormatting.GRAY))
				.addLoreLine(Component.literal("For looking at only").withStyle(ChatFormatting.DARK_GRAY)).build());
		gui.open();
	}

	/** Opens "All Skins" on its first page: every skin on the server, owned or not. */
	public static void openAll(ServerPlayer player, Skins skins) {
		allSkins(player, skins, 0);
	}

	private static void allSkins(ServerPlayer player, Skins skins, int requestedPage) {
		List<SkinSummary> all = new ArrayList<>(skins.allSkins());
		all.sort(Comparator.comparing((SkinSummary s) -> s.dormant() || s.kind().isEmpty()).thenComparing(order(skins)));
		int pages = Math.max(1, (all.size() + PER_PAGE - 1) / PER_PAGE);
		int page = Math.clamp(requestedPage, 0, pages - 1);

		SimpleGui gui = locked(MenuType.GENERIC_9x6, player);
		gui.setTitle(Component.literal("All Skins" + (pages > 1 ? " (" + (page + 1) + "/" + pages + ")" : "")));
		List<SkinSummary> shown = all.subList(page * PER_PAGE, Math.min(all.size(), (page + 1) * PER_PAGE));
		for (int i = 0; i < shown.size(); i++) {
			SkinSummary skin = shown.get(i);
			gui.setSlot(i, skin.dormant() || skin.fit().isEmpty() ? dormant(skin) : look(skin, skin.fit().get()).build());
		}
		if (all.isEmpty()) {
			gui.setSlot(MIDDLE, new GuiElementBuilder(Items.PAPER).setName(Component.literal("There are no skins on this server yet")).build());
		}
		for (int i = PER_PAGE; i < PER_PAGE + 9; i++) gui.setSlot(i, filler());
		if (page > 0) gui.setSlot(PREVIOUS_PAGE, arrow("Previous page", () -> allSkins(player, skins, page - 1)));
		if (page < pages - 1) gui.setSlot(NEXT_PAGE, arrow("Next page", () -> allSkins(player, skins, page + 1)));
		gui.setSlot(BOOK, book());
		gui.open();
	}

	/** In place of the skins, while the player's skins file is damaged. */
	private static eu.pb4.sgui.api.elements.GuiElement damaged() {
		return new GuiElementBuilder(Items.BARRIER).setName(Component.literal("Your skins can't be shown").withStyle(ChatFormatting.RED))
				.addLoreLine(Component.literal("Your skins file is damaged. Ask an admin to fix it.").withStyle(ChatFormatting.GRAY)).build();
	}

	/**
	 * A skin the player doesn't own, in "Your Skins", named "???", giving nothing else away: a light
	 * grey dye, "Not owned yet"; or, for an unobtainable one, a grey dye, "Unobtainable".
	 */
	private static eu.pb4.sgui.api.elements.GuiElement notOwned(SkinSummary skin) {
		boolean obtainable = skin.release().map(Release::obtainable).orElse(true);
		return new GuiElementBuilder(obtainable ? Items.DYE.lightGray() : Items.DYE.gray()).setName(Component.literal("???").withStyle(ChatFormatting.BOLD))
				.addLoreLine(obtainable ? Component.literal("Not owned yet").withStyle(ChatFormatting.GRAY) : Component.literal("Unobtainable").withStyle(ChatFormatting.RED))
				.hideDefaultTooltip().build();
	}

	/** A Dormant skin in "All Skins": it has no Look to show, so a barrier with its id and why it didn't load. */
	private static eu.pb4.sgui.api.elements.GuiElement dormant(SkinSummary skin) {
		return new GuiElementBuilder(Items.BARRIER).setName(Component.literal(skin.id()).withStyle(ChatFormatting.GRAY))
				.addLoreLine(Component.literal("Dormant: " + skin.dormantReason().orElse("its Kind is unknown")).withStyle(ChatFormatting.RED)).build();
	}

	/** "How skins work": sums skins up in its tooltip. */
	private static eu.pb4.sgui.api.elements.GuiElement book() {
		return new GuiElementBuilder(Items.BOOK).setName(Component.literal("How skins work"))
				.addLoreLine(Component.literal("Claim a Skin Token to own a skin.").withStyle(ChatFormatting.GRAY))
				.addLoreLine(Component.literal("Click a skin to put it on one item.").withStyle(ChatFormatting.GRAY))
				.addLoreLine(Component.literal("A skin is on one item at a time.").withStyle(ChatFormatting.GRAY))
				.addLoreLine(Component.literal("Extract turns a skin back into its token.").withStyle(ChatFormatting.GRAY))
				.addLoreLine(Component.literal("Open a Skin Box to roll for a token.").withStyle(ChatFormatting.GRAY)).build();
	}

	/** "Apply: <skin>" on page {@code requestedPage} of its items; {@code fromPage} is the page of "Your Skins" to go back to. */
	private static void applyScreen(ServerPlayer player, Skins skins, String skinId, int fromPage, int requestedPage) {
		Optional<Listed> found = listed(skins, skins.ownedSkins(player.getUUID())).stream()
				.filter(l -> l.owned().isPresent() && l.skin().id().equals(skinId)).findFirst();
		if (found.isEmpty()) {
			// Revoked, gone Dormant or a damaged file since the list was drawn.
			Feedback.failed(player, "That skin isn't yours to apply right now.");
			yourSkins(player, skins, fromPage);
			return;
		}
		Listed skin = found.get();
		String name = skin.skin().displayName();

		List<Candidate> candidates = skins.candidates(player, skinId);
		int pages = Math.max(1, (candidates.size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
		int page = Math.clamp(requestedPage, 0, pages - 1);

		SimpleGui gui = locked(MenuType.GENERIC_9x4, player);
		gui.setTitle(Component.literal("Apply: " + name + (pages > 1 ? " (" + (page + 1) + "/" + pages + ")" : "")));
		for (int i = 0; i < FIRST_ITEM; i++) gui.setSlot(i, filler());
		gui.setSlot(BACK, new GuiElementBuilder(Items.ARROW).setName(Component.literal("Back to your skins"))
				.setCallback(() -> yourSkins(player, skins, fromPage)).build());
		gui.setSlot(PREVIEW, icon(skin).build());
		Optional<Tint> tint = skin.tint();
		if (tint.isPresent()) {
			gui.setSlot(CLEAR_TINT, new GuiElementBuilder(Items.WATER_BUCKET).setName(Component.literal("Clear tint"))
					.addLoreLine(Component.literal("Tint: ").withStyle(ChatFormatting.GRAY).append(Component.literal(tint.get().hex())
							.withStyle(s -> s.withColor(tint.get().rgb()))))
					.addLoreLine(Component.literal("Puts " + name + " back in its starting colour").withStyle(ChatFormatting.GRAY))
					.setCallback(() -> clearTint(player, skins, skinId, name, fromPage)).build());
		}
		if (skin.applied()) {
			gui.setSlot(REMOVE, new GuiElementBuilder(Items.GRINDSTONE).setName(Component.literal("Remove " + name))
					.addLoreLine(Component.literal("Takes it off its item, wherever that is").withStyle(ChatFormatting.GRAY))
					.setCallback(() -> remove(player, skins, skinId, name, fromPage)).build());
		}

		if (page > 0) gui.setSlot(PREVIOUS_ITEMS, arrow("Previous page", () -> applyScreen(player, skins, skinId, fromPage, page - 1)));
		if (page < pages - 1) gui.setSlot(NEXT_ITEMS, arrow("Next page", () -> applyScreen(player, skins, skinId, fromPage, page + 1)));
		if (candidates.isEmpty()) {
			gui.setSlot(MIDDLE, new GuiElementBuilder(Items.BARRIER).setName(Component.literal("You aren't carrying " + skin.fit().aOne()).withStyle(ChatFormatting.RED))
					.addLoreLine(Component.literal(name + " fits " + skin.fit().what() + ".").withStyle(ChatFormatting.GRAY))
					.addLoreLine(Component.literal("Pick one up and run /itemskins again.").withStyle(ChatFormatting.GRAY)).build());
		}
		List<Candidate> shown = candidates.subList(page * ITEMS_PER_PAGE, Math.min(candidates.size(), (page + 1) * ITEMS_PER_PAGE));
		for (int i = 0; i < shown.size(); i++) {
			Candidate candidate = shown.get(i);
			GuiElementBuilder item = GuiElementBuilder.from(asInTheirInventory(skins, player, candidate.item()))
					.addLoreLine(Component.literal(candidate.where().label()).withStyle(ChatFormatting.GRAY));
			if (candidate.alreadyHasIt()) {
				item.addLoreLine(Component.literal("Already has this skin").withStyle(ChatFormatting.AQUA)).glow();
			} else if (candidate.replaces().isPresent()) {
				item.addLoreLine(Component.literal("Replaces " + candidate.replaces().get()).withStyle(ChatFormatting.RED));
			} else {
				item.addLoreLine(Component.literal("Click to apply").withStyle(ChatFormatting.YELLOW));
			}
			gui.setSlot(FIRST_ITEM + i, item.setCallback(() -> apply(player, skins, skinId, name, candidate, fromPage, page)).build());
		}
		gui.open();
	}

	/**
	 * An item on the player as their own inventory shows it, for a menu slot (which isn't their
	 * inventory, so would draw it plain): a copy without the marker, drawn as being with them.
	 */
	private static ItemStack asInTheirInventory(Skins skins, ServerPlayer player, ItemStack item) {
		ItemStack copy = item.copy();
		copy.remove(SkinMarker.TYPE);
		return skins.clientView(item, copy, player.getUUID());
	}

	private static void apply(ServerPlayer player, Skins skins, String skinId, String name, Candidate candidate,
			int fromPage, int itemsPage) {
		String itemName = candidate.item().getHoverName().getString();
		ApplyResult result = skins.apply(player, skinId, candidate.where());
		if (result.applied()) {
			Feedback.applied(player, name, itemName, result.replaced());
			yourSkins(player, skins, fromPage);
			return;
		}
		Feedback.failed(player, Feedback.whyNot(result.outcome(), name, itemName));
		applyScreen(player, skins, skinId, fromPage, itemsPage);
	}

	private static void clearTint(ServerPlayer player, Skins skins, String skinId, String name, int fromPage) {
		OwnershipChange result = skins.clearTint(player, skinId);
		if (result == OwnershipChange.DONE) {
			Feedback.tintCleared(player, name);
		} else {
			Feedback.failed(player, Feedback.whyNot(result, name, CLEARING));
		}
		applyScreen(player, skins, skinId, fromPage, 0);
	}

	private static void remove(ServerPlayer player, Skins skins, String skinId, String name, int fromPage) {
		OwnershipChange result = skins.remove(player.nameAndId(), skinId);
		if (result == OwnershipChange.DONE) {
			skins.tidy(player);   // the item it was on, if they have it
			Feedback.removed(player, name);
		} else {
			Feedback.failed(player, Feedback.whyNot(result, name, REMOVING));
		}
		yourSkins(player, skins, fromPage);
	}

	/**
	 * A skin shown with its Look: a vanilla item it fits ({@link Fit#icon()}) wearing the skin's item model, its own tooltip hidden,
	 * named in the skin's colours, then what it fits, its release, Perks and description.
	 */
	private static GuiElementBuilder look(SkinSummary skin, Fit fit) {
		GuiElementBuilder icon = lookOnly(skin, fit);
		lookLines(skin, fit).forEach(icon::addLoreLine);
		return icon;
	}

	/** A skin's Look, with no lore: a vanilla item it fits wearing the skin's item model, its own tooltip hidden, named in the skin's colours. */
	static GuiElementBuilder lookOnly(SkinSummary skin, Fit fit) {
		return GuiElementBuilder.from(new ItemStack(fit.icon()))
				.model(Identifier.fromNamespaceAndPath(SkinFolder.NAMESPACE, skin.id()))
				.setName(skin.styledName())
				.hideDefaultTooltip();
	}

	/** The lines under a skin's name wherever menus show its Look: what it fits (with the dye hint), its release, Perks and description. */
	static List<Component> lookLines(SkinSummary skin, Fit fit) {
		List<Component> lines = new ArrayList<>(fit.lore(skin.dyeable()));
		skin.release().ifPresent(r -> lines.add(r.line()));
		lines.addAll(PerkLines.of(skin.perks()));
		skin.description().ifPresent(d -> lines.add(Component.literal(d).withStyle(ChatFormatting.GRAY)));
		return lines;
	}

	/** An owned skin: its {@link #look} (a Dyeable Hat in its saved tint), then whether it's applied (glinting while it is). */
	private static GuiElementBuilder icon(Listed skin) {
		GuiElementBuilder icon = look(skin.skin(), skin.fit());
		skin.tint().ifPresent(tint -> icon.setComponent(DataComponents.DYED_COLOR, new DyedItemColor(tint.rgb())));
		icon.addLoreLine(skin.applied() ? Component.literal("✔ Applied to an item").withStyle(ChatFormatting.GREEN)
				: Component.literal("Unapplied").withStyle(ChatFormatting.GRAY));
		return icon.glow(skin.applied());
	}

	/** A chest menu that only ever reacts to its own buttons: the player's own inventory is locked. */
	static SimpleGui locked(MenuType<?> type, ServerPlayer player) {
		SimpleGui gui = new SimpleGui(type, player, false);
		gui.setLockPlayerInventory(true);
		return gui;
	}

	/** A page arrow, on either screen. */
	private static eu.pb4.sgui.api.elements.GuiElement arrow(String name, Runnable turn) {
		return new GuiElementBuilder(Items.ARROW).setName(Component.literal(name)).setCallback(turn).build();
	}

	private static eu.pb4.sgui.api.elements.GuiElement filler() {
		return new GuiElementBuilder(Items.STAINED_GLASS_PANE.gray()).setName(Component.literal(" ")).hideTooltip().build();
	}
}
