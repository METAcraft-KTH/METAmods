package se.metacraft.skins.ui;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import se.metacraft.skins.ApplyResult;
import se.metacraft.skins.Candidate;
import se.metacraft.skins.Fit;
import se.metacraft.skins.OwnedSkin;
import se.metacraft.skins.OwnershipChange;
import se.metacraft.skins.PerkLines;
import se.metacraft.skins.PlayerSkins;
import se.metacraft.skins.SkinFolder;
import se.metacraft.skins.SkinMarker;
import se.metacraft.skins.SkinSummary;
import se.metacraft.skins.Skins;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The Skin Menu ({@code /itemskins}): two chest screens, built with sgui, that only ever call the
 * {@link Skins} module, and a third for {@code /itemskins list}. The player's own inventory below
 * stays visible but locked on all of them.
 * <ul>
 *   <li><b>"Your Skins"</b> (6 rows): rows 1–5 the player's owned, non-Dormant skins, 45 a page,
 *       sorted by Kind, each shown with its Look and glinting while applied; row 6 the page
 *       arrows (only with more than one page) and the "How skins work" book, which sums skins up
 *       in its tooltip.</li>
 *   <li><b>"Apply: &lt;skin&gt;"</b> (4 rows), opened by clicking a skin: Back, a preview and, while
 *       the skin is applied, Remove along row 1; below, every item on the player the skin fits,
 *       hotbar to offhand, 27 a page, each shown as their own inventory shows it and saying where it
 *       is and what clicking it does. With more
 *       than one page, arrows either side of the preview turn them. Clicking an item applies the
 *       skin and goes back to "Your Skins".</li>
 *   <li><b>"All Skins"</b> ({@code /itemskins list}): "Your Skins", but with every skin on the server,
 *       owned or not, and nothing to click; Dormant skins come last, as barriers saying why.</li>
 * </ul>
 * Every skin is shown with its Look, named in its colours ({@link se.metacraft.skins.SkinName}), and
 * says when it was released and whether it's obtainable ({@link se.metacraft.skins.Release}).
 */
public final class SkinMenu {
	private SkinMenu() {}

	static final int PER_PAGE = 45;
	/** "Your Skins": row 6. */
	static final int PREVIOUS_PAGE = 45, BOOK = 49, NEXT_PAGE = 53;
	/** "Apply: <skin>": row 1, then the items from row 2 on. */
	static final int BACK = 0, PREVIOUS_ITEMS = 3, PREVIEW = 4, NEXT_ITEMS = 5, REMOVE = 8, FIRST_ITEM = 9, MIDDLE = 22, ITEMS_PER_PAGE = 27;
	/** How a refused Remove is worded. */
	private static final Feedback.Audience REMOVING = new Feedback.Audience.Owner("remove", "nothing changed");

	/**
	 * Opens "Your Skins" on its first page, once the player's own items are tidied ({@link Skins#tidy}):
	 * a skin that fell off its item (a smithing upgrade past its Materials) is Unapplied by the time it's listed.
	 */
	public static void open(ServerPlayer player, Skins skins) {
		skins.tidy(player);
		yourSkins(player, skins, 0);
	}

	/** One owned skin as the menu lists it. */
	private record Listed(OwnedSkin owned, SkinSummary skin, Fit fit) {
		boolean applied() {
			return owned.state() == OwnedSkin.State.APPLIED;
		}
	}

	private static List<Listed> listed(Skins skins, PlayerSkins owned) {
		List<Listed> listed = new ArrayList<>();
		for (OwnedSkin skin : owned.skins()) {
			if (skin.state() == OwnedSkin.State.DORMANT) continue;
			skins.skin(skin.id()).filter(s -> !s.dormant() && s.kind().isPresent()).ifPresent(s -> listed.add(new Listed(skin, s, s.fit().orElseThrow())));
		}
		listed.sort(Comparator.comparing((Listed l) -> l.fit().kind().ordinal()).thenComparing(l -> l.skin().displayName()).thenComparing(l -> l.skin().id()));
		return listed;
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
			gui.setSlot(i, icon(skin).addLoreLine(Component.literal("Click to apply or remove").withStyle(ChatFormatting.DARK_GRAY))
					.setCallback(() -> applyScreen(player, skins, skin.skin().id(), page, 0)).build());
		}
		if (owned.damage().isPresent()) {
			gui.setSlot(MIDDLE, new GuiElementBuilder(Items.BARRIER).setName(Component.literal("Your skins can't be shown").withStyle(ChatFormatting.RED))
					.addLoreLine(Component.literal("Your skins file is damaged. Ask an admin to fix it.").withStyle(ChatFormatting.GRAY)).build());
		} else if (listed.isEmpty()) {
			gui.setSlot(MIDDLE, new GuiElementBuilder(Items.PAPER).setName(Component.literal("You don't own any skins yet"))
					.addLoreLine(Component.literal("Claim a Skin Token to own one.").withStyle(ChatFormatting.GRAY)).build());
		}
		for (int i = PER_PAGE; i < PER_PAGE + 9; i++) gui.setSlot(i, filler());
		if (page > 0) gui.setSlot(PREVIOUS_PAGE, arrow("Previous page", () -> yourSkins(player, skins, page - 1)));
		if (page < pages - 1) gui.setSlot(NEXT_PAGE, arrow("Next page", () -> yourSkins(player, skins, page + 1)));
		gui.setSlot(BOOK, book());
		gui.open();
	}

	/** Opens "All Skins" on its first page: every skin on the server, owned or not. */
	public static void openAll(ServerPlayer player, Skins skins) {
		allSkins(player, skins, 0);
	}

	private static void allSkins(ServerPlayer player, Skins skins, int requestedPage) {
		List<SkinSummary> all = new ArrayList<>(skins.allSkins());
		all.sort(Comparator.comparing((SkinSummary s) -> s.dormant() || s.kind().isEmpty())
				.thenComparing(s -> s.kind().map(Enum::ordinal).orElse(Integer.MAX_VALUE))
				.thenComparing(SkinSummary::displayName).thenComparing(SkinSummary::id));
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
				.addLoreLine(Component.literal("A skin is on one item at a time.").withStyle(ChatFormatting.GRAY)).build();
	}

	/** "Apply: <skin>" on page {@code requestedPage} of its items; {@code fromPage} is the page of "Your Skins" to go back to. */
	private static void applyScreen(ServerPlayer player, Skins skins, String skinId, int fromPage, int requestedPage) {
		Optional<Listed> found = listed(skins, skins.ownedSkins(player.getUUID())).stream().filter(l -> l.skin().id().equals(skinId)).findFirst();
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
		GuiElementBuilder icon = GuiElementBuilder.from(new ItemStack(fit.icon()))
				.model(Identifier.fromNamespaceAndPath(SkinFolder.NAMESPACE, skin.id()))
				.setName(skin.styledName())
				.hideDefaultTooltip();
		fit.lore(skin.dyeable()).forEach(icon::addLoreLine);
		skin.release().ifPresent(r -> icon.addLoreLine(r.line()));
		PerkLines.of(skin.perks()).forEach(icon::addLoreLine);
		skin.description().ifPresent(d -> icon.addLoreLine(Component.literal(d).withStyle(ChatFormatting.GRAY)));
		return icon;
	}

	/** An owned skin: its {@link #look}, then whether it's applied (glinting while it is). */
	private static GuiElementBuilder icon(Listed skin) {
		GuiElementBuilder icon = look(skin.skin(), skin.fit());
		icon.addLoreLine(skin.applied() ? Component.literal("✔ Applied to an item").withStyle(ChatFormatting.GREEN)
				: Component.literal("Unapplied").withStyle(ChatFormatting.GRAY));
		return icon.glow(skin.applied());
	}

	private static SimpleGui locked(MenuType<?> type, ServerPlayer player) {
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
