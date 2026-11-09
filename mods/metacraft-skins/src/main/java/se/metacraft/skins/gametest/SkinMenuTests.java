package se.metacraft.skins.gametest;

import eu.pb4.sgui.api.containerwrappers.SlotBasedWrapperMenu;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import se.metacraft.skins.ItemLocation;
import se.metacraft.skins.OwnedSkin;
import se.metacraft.skins.SkinMarker;
import se.metacraft.skins.item.SkinToken;
import se.metacraft.skins.ui.SkinMenu;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.HashedStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;
import java.util.Optional;

import static se.metacraft.skins.gametest.SkinFixtures.assertThat;

/**
 * Wiring only: the Skin Menu clicked the way a client clicks it (container-click packets through
 * the player's connection, so sgui's own handling runs). The rules behind each click are
 * ApplyTests'. The menu gets the test's Skins directly, so nothing global is swapped.
 */
public final class SkinMenuTests {
	static final int FIRST_ITEM = 9;
	private static final int PREVIEW = 4, CLEAR_TINT = 7, REMOVE = 8, MIDDLE = 22, PREVIOUS_PAGE = 45, BOOK = 49, EXTRACT = 50, NEXT_PAGE = 53;
	/** "Apply: <skin>"'s page arrows, either side of the preview. */
	private static final int APPLY_PREVIOUS_PAGE = 3, APPLY_NEXT_PAGE = 5;

	@GameTest
	public void clickingThroughTheMenuAppliesAndRemovesASkin(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().skin("crown_test", "helmet", "Crown", null)
				.perky("candy_sword_test", "sword", "Candy Sword", "Sweet.", "[ { trigger: \"kill\", effects: [ { type: \"heal\", amount: 4 } ] } ]")
				.skin("gone", "sword");
		fx.reload();
		SkinFixtures.Listening player = SkinFixtures.join(helper);
		try {
			player.getInventory().clearContent();
			player.getInventory().setItem(0, new ItemStack(Items.DIAMOND_SWORD));
			player.getInventory().setItem(5, new ItemStack(Items.DIRT, 10));
			for (String skin : List.of("crown_test", "candy_sword_test", "gone")) fx.skins.grant(player.nameAndId(), skin);
			fx.delete("gone");
			fx.reload();
			SkinMenu.open(player, fx.skins);

			assertThat(title(player).equals("Your Skins"), "the menu is " + title(player));
			assertThat(rows(player) == 6, "Your Skins has " + rows(player) + " rows");
			// Neither has a date, so by name; the Dormant skin isn't listed.
			assertThat(name(player, 0).equals("Candy Sword") && name(player, 1).equals("Crown") && slot(player, 2).isEmpty(),
					"listed " + name(player, 0) + ", " + name(player, 1) + ", " + name(player, 2));
			assertThat(lore(player, 0).equals(List.of("Fits any sword", "Obtainable", "On kill: heals 2 hearts", "Sweet.", "Unapplied", "Click to apply or remove")),
					"the skin's tooltip: " + lore(player, 0));
			assertThat("skins:candy_sword_test".equals(String.valueOf(slot(player, 0).get(DataComponents.ITEM_MODEL))), "the icon isn't the Look");
			assertThat(!slot(player, PREVIOUS_PAGE).is(Items.ARROW) && !slot(player, NEXT_PAGE).is(Items.ARROW), "page arrows with one page");
			assertThat(name(player, BOOK).equals("How skins work"), "row 6 has no book: " + name(player, BOOK));

			// The player's own inventory is locked: a click on their hotbar moves nothing.
			click(player, rows(player) * 9 + 27);
			assertThat(player.getInventory().getItem(0).is(Items.DIAMOND_SWORD) && player.containerMenu.getCarried().isEmpty(), "the inventory isn't locked");

			click(player, 0);
			assertThat(title(player).equals("Apply: Candy Sword") && rows(player) == 4, "clicking the skin opened " + title(player));
			assertThat(name(player, 4).equals("Candy Sword") && !slot(player, REMOVE).is(Items.GRINDSTONE), "the apply screen's top row");
			assertThat(lore(player, FIRST_ITEM).containsAll(List.of("Hotbar slot 1", "Click to apply")), "the sword's label: " + lore(player, FIRST_ITEM));
			assertThat(slot(player, FIRST_ITEM + 1).isEmpty(), "the dirt is a candidate");

			click(player, FIRST_ITEM);
			assertThat(title(player).equals("Your Skins"), "applying went to " + title(player));
			assertThat(fx.skins.liveSkin(player.getInventory().getItem(0)).isPresent(), "the sword wasn't skinned");
			String applied = "✔ Candy Sword applied to your Diamond Sword";
			assertThat(player.heard.contains(applied) && player.actionBar.contains(applied), "the player was told " + player.heard);
			assertThat(lore(player, 0).contains("✔ Applied to an item") && Boolean.TRUE.equals(slot(player, 0).get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)),
					"an applied skin doesn't say so or glint: " + lore(player, 0));

			click(player, 0);
			assertThat(lore(player, FIRST_ITEM).contains("Already has this skin"), "the skinned sword's label: " + lore(player, FIRST_ITEM));
			assertThat(slot(player, REMOVE).is(Items.GRINDSTONE), "no Remove while applied");
			click(player, REMOVE);
			assertThat(title(player).equals("Your Skins"), "removing went to " + title(player));
			assertThat(fx.skins.ownedSkins(player.getUUID()).skins().stream().allMatch(s -> s.state() != OwnedSkin.State.APPLIED), "the skin is still applied");
			assertThat(player.heard.contains("Candy Sword removed. The item it was on is plain again."), "the player was told " + player.heard);
			assertThat(!player.getInventory().getItem(0).has(SkinMarker.TYPE), "Remove didn't tidy the player's sword");
		} finally {
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
		helper.succeed();
	}

	/**
	 * Opening the Skin Menu tidies the player's own items before it lists anything: a skin that fell
	 * off (here through the netherite upgrade, past a diamond-only skin's Materials) reads Unapplied,
	 * and its item loses the marker.
	 */
	@GameTest
	public void openingTheMenuTidiesAndMakesASkinThatFellOffUnapplied(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().limited("diamond_blade_test", "sword", "[\"diamond\"]");
		fx.reload();
		SkinFixtures.Listening player = SkinFixtures.join(helper);
		try {
			player.getInventory().clearContent();
			player.getInventory().setItem(0, new ItemStack(Items.DIAMOND_SWORD));
			fx.skins.grant(player.nameAndId(), "diamond_blade_test");
			fx.skins.apply(player, "diamond_blade_test", ItemLocation.hotbar(0));
			// What the smithing table's netherite upgrade does to its base: the same components on a netherite sword.
			ItemStack upgraded = player.getInventory().getItem(0).transmuteCopy(Items.NETHERITE_SWORD);
			player.getInventory().setItem(0, upgraded);
			assertThat(upgraded.has(SkinMarker.TYPE), "the upgrade lost the marker");

			SkinMenu.open(player, fx.skins);
			assertThat(lore(player, 0).contains("Unapplied"), "the skin that fell off reads " + lore(player, 0));
			assertThat(!upgraded.has(SkinMarker.TYPE), "opening the Skin Menu didn't tidy the upgraded sword");
		} finally {
			player.closeContainer();
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
		helper.succeed();
	}

	@GameTest
	public void theApplyScreenSaysWhatEachItemWouldDo(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().skin("crown_test", "helmet", "Crown", null).skin("candy_sword_test", "sword", "Candy Sword", null)
				.skin("frost", "sword", "Frost Blade", null);
		fx.reload();
		SkinFixtures.Listening player = SkinFixtures.join(helper);
		try {
			player.getInventory().clearContent();
			player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.IRON_SWORD));
			for (String skin : List.of("crown_test", "candy_sword_test", "frost")) fx.skins.grant(player.nameAndId(), skin);
			fx.skins.apply(player, "frost", ItemLocation.offhand());
			SkinMenu.open(player, fx.skins);

			click(player, 1);   // by name: Candy Sword, Crown, Frost Blade
			assertThat(title(player).equals("Apply: Crown"), "opened " + title(player));
			assertThat(slot(player, MIDDLE).is(Items.BARRIER) && name(player, MIDDLE).equals("You aren't carrying a helmet"),
					"with no helmet: " + name(player, MIDDLE));
			click(player, 0);   // Back
			click(player, 0);
			assertThat(title(player).equals("Apply: Candy Sword"), "opened " + title(player));
			assertThat(lore(player, FIRST_ITEM).containsAll(List.of("Offhand", "Replaces Frost Blade")), "the offhand sword's label: " + lore(player, FIRST_ITEM));
			click(player, FIRST_ITEM);
			assertThat(player.heard.contains("✔ Candy Sword applied to your Iron Sword (replaced Frost Blade)"), "the player was told " + player.heard);

			// A failure says so in red and stays on the apply screen.
			player.getInventory().setItem(3, new ItemStack(Items.GOLDEN_SWORD));
			click(player, 2);
			player.getInventory().setItem(3, ItemStack.EMPTY);
			click(player, FIRST_ITEM);
			assertThat(player.heard.getLast().equals("That Golden Sword isn't there any more."), "the player was told " + player.heard);
			assertThat(title(player).equals("Apply: Frost Blade"), "a failed apply went to " + title(player));
		} finally {
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
		helper.succeed();
	}

	/**
	 * The apply screen shows each item as it looks in the player's own inventory: their skinned
	 * sword with its Look and tooltip block, a sword lent by someone else Away. No icon carries the marker.
	 */
	@GameTest
	public void theApplyScreenShowsEachItemAsItLooksInTheirInventory(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().skin("candy_sword_test", "sword", "Candy Sword", null).skin("frost", "sword", "Frost Blade", null);
		fx.reload();
		SkinFixtures.Listening player = SkinFixtures.join(helper);
		ServerPlayer bob = ApplyTests.carrying(helper, new ItemStack(Items.IRON_SWORD));
		try {
			player.getInventory().clearContent();
			player.getInventory().setItem(0, new ItemStack(Items.DIAMOND_SWORD));
			for (String skin : List.of("candy_sword_test", "frost")) fx.skins.grant(player.nameAndId(), skin);
			fx.skins.apply(player, "frost", ItemLocation.hotbar(0));
			fx.skins.grant(bob.nameAndId(), "frost");
			fx.skins.apply(bob, "frost", ItemLocation.hotbar(0));
			player.getInventory().setItem(1, bob.getInventory().removeItemNoUpdate(0));
			SkinMenu.open(player, fx.skins);
			click(player, 0);
			assertThat(title(player).equals("Apply: Candy Sword"), "opened " + title(player));

			ItemStack theirs = slot(player, FIRST_ITEM);
			assertThat("skins:frost".equals(String.valueOf(theirs.get(DataComponents.ITEM_MODEL))), "their skinned sword shows " + theirs.get(DataComponents.ITEM_MODEL));
			assertThat(lore(player, FIRST_ITEM).containsAll(List.of("Obtainable", "Owned by " + player.getGameProfile().name(), "Hotbar slot 1")),
					"their skinned sword reads " + lore(player, FIRST_ITEM));
			ItemStack lent = slot(player, FIRST_ITEM + 1);
			assertThat("minecraft:iron_sword".equals(String.valueOf(lent.get(DataComponents.ITEM_MODEL))), "the lent sword shows " + lent.get(DataComponents.ITEM_MODEL));
			assertThat(lore(player, FIRST_ITEM + 1).contains("Frost Blade skin, only works for " + bob.getGameProfile().name()), "the lent sword reads "
					+ lore(player, FIRST_ITEM + 1));
			assertThat(!theirs.has(SkinMarker.TYPE) && !lent.has(SkinMarker.TYPE), "an icon carries the marker");
		} finally {
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
		helper.succeed();
	}

	/** The apply screen lists only items the skin fits, Material included: a diamond helmet isn't offered a leather-only skin. */
	@GameTest
	public void theApplyScreenHidesItemsOfTheWrongMaterial(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().limited("cap", "helmet", "[\"leather\"]");
		fx.reload();
		SkinFixtures.Listening player = SkinFixtures.join(helper);
		try {
			player.getInventory().clearContent();
			player.getInventory().setItem(0, new ItemStack(Items.DIAMOND_HELMET));
			player.getInventory().setItem(1, new ItemStack(Items.LEATHER_HELMET));
			player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.TURTLE_HELMET));
			fx.skins.grant(player.nameAndId(), "cap");
			SkinMenu.open(player, fx.skins);

			click(player, 0);
			assertThat(title(player).equals("Apply: cap skin"), "opened " + title(player));
			assertThat(slot(player, FIRST_ITEM).is(Items.LEATHER_HELMET) && lore(player, FIRST_ITEM).contains("Hotbar slot 2"),
					"the first item offered: " + slot(player, FIRST_ITEM) + " " + lore(player, FIRST_ITEM));
			assertThat(slot(player, FIRST_ITEM + 1).isEmpty(), "a helmet of the wrong Material is offered: " + slot(player, FIRST_ITEM + 1));
		} finally {
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
		helper.succeed();
	}

	/**
	 * A limited skin says which Materials it fits, in vanilla order, and is shown on an item it
	 * fits: the diamond one if it fits diamond, else its last Material's. With nothing that fits,
	 * the apply screen names those Materials too. Unlimited skins read and show as before.
	 */
	@GameTest
	public void aLimitedSkinSaysWhatItFitsAndShowsOnAnItemItFits(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().limited("blades", "sword", "[\"netherite\", \"diamond\"]").limited("irons", "sword", "[\"iron\"]")
				.skin("plain", "sword").limited("cap", "helmet", "[\"leather\"]").limited("boots", "boots", "[\"leather\"]");
		fx.reload();
		SkinFixtures.Listening player = SkinFixtures.join(helper);
		try {
			player.getInventory().clearContent();
			for (String skin : List.of("blades", "irons", "plain", "cap", "boots")) fx.skins.grant(player.nameAndId(), skin);
			SkinMenu.open(player, fx.skins);
			// None has a date, so by name.
			List<Shown> shown = List.of(
					new Shown("blades skin", Items.DIAMOND_SWORD, "diamond and netherite swords", "a diamond or netherite sword"),
					new Shown("boots skin", Items.LEATHER_BOOTS, "leather boots", "leather boots"),
					new Shown("cap skin", Items.LEATHER_HELMET, "leather helmets", "a leather helmet"),
					new Shown("irons skin", Items.IRON_SWORD, "iron swords", "an iron sword"),
					new Shown("plain skin", Items.DIAMOND_SWORD, "any sword", "a sword"));
			for (int i = 0; i < shown.size(); i++) {
				Shown expected = shown.get(i);
				assertThat(name(player, i).equals(expected.name()), "slot " + i + " is " + name(player, i));
				assertThat(slot(player, i).is(expected.icon()) && !slot(player, i).has(DataComponents.DYED_COLOR),
						expected.name() + " is shown on " + slot(player, i) + " " + slot(player, i).get(DataComponents.DYED_COLOR));
				assertThat(lore(player, i).getFirst().equals("Fits " + expected.fits()), expected.name() + "'s tooltip: " + lore(player, i));

				click(player, i);
				assertThat(title(player).equals("Apply: " + expected.name()), "opened " + title(player));
				assertThat(slot(player, PREVIEW).is(expected.icon()), expected.name() + "'s preview is " + slot(player, PREVIEW));
				assertThat(slot(player, MIDDLE).is(Items.BARRIER) && name(player, MIDDLE).equals("You aren't carrying " + expected.carrying()),
						"with nothing that fits " + expected.name() + ": " + name(player, MIDDLE));
				assertThat(lore(player, MIDDLE).getFirst().equals(expected.name() + " fits " + expected.fits() + "."), "the barrier's lore: " + lore(player, MIDDLE));
				click(player, 0);   // Back
			}
		} finally {
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
		helper.succeed();
	}

	/** A Dyeable Hat says, under what it fits, that dyeing the helmet recolours it: in "Your Skins" and in its Apply screen's preview. */
	@GameTest
	public void aDyeableHatSaysDyeingRecoloursIt(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().dyeableHat("cowboy_hat_test", "[\"leather\"]");
		fx.reload();
		SkinFixtures.Listening player = SkinFixtures.join(helper);
		try {
			player.getInventory().clearContent();
			fx.skins.grant(player.nameAndId(), "cowboy_hat_test");
			SkinMenu.open(player, fx.skins);
			assertThat(lore(player, 0).equals(List.of("Fits leather helmets", "Dye the helmet to recolour it", "Obtainable", "Unapplied", "Click to apply or remove")),
					"the hat's tooltip: " + lore(player, 0));
			click(player, 0);
			assertThat(lore(player, PREVIEW).equals(List.of("Fits leather helmets", "Dye the helmet to recolour it", "Obtainable", "Unapplied")),
					"the preview's tooltip: " + lore(player, PREVIEW));
		} finally {
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
		helper.succeed();
	}

	private record Shown(String name, net.minecraft.world.item.Item icon, String fits, String carrying) {}

	/**
	 * "Your Skins" lists every skin that loaded, unobtainable ones too, oldest release first and
	 * undated ones last, by name: the player's own with their Look, the rest as a light grey dye named
	 * a bold "???" that clicking does nothing for. Owning one turns its "???" into its Look in the same slot.
	 */
	@GameTest
	public void skinsNotOwnedShowAsQuestionMarksInReleaseOrder(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().skin("crown_test", "helmet", "Crown", null).skin("candy_sword_test", "sword", "Candy Sword", null)
				.skin("old_test", "helmet").file("old_test", "skin.json5", "{ name: \"Zebra Hat\", kind: \"helmet\", released: \"2026/10/01\","
						+ " look: { model: \"old_test.json\" } }")
				.skin("new_test", "helmet").file("new_test", "skin.json5", "{ name: \"Apple Hat\", kind: \"helmet\", released: \"2026/11/01\","
						+ " obtainable: false, look: { model: \"new_test.json\" } }")
				.lookOnly("hat", "hat");
		fx.reload();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		fx.skins.grant(player.nameAndId(), "crown_test");
		SkinMenu.open(player, fx.skins);

		// Zebra Hat (2026/10/01), Apple Hat (2026/11/01), then the undated Candy Sword and Crown; the Dormant hat isn't listed.
		// Apple Hat is unobtainable: a grey dye rather than a light grey one, saying so.
		for (int i = 0; i < 3; i++) {
			boolean obtainable = i != 1;
			assertThat(slot(player, i).is(obtainable ? Items.DYE.lightGray() : Items.DYE.gray()) && name(player, i).equals("???") && boldName(player, i)
					&& lore(player, i).equals(List.of(obtainable ? "Not owned yet" : "Unobtainable")),
					"slot " + i + " is " + slot(player, i) + " " + name(player, i) + " " + lore(player, i));
		}
		assertThat(name(player, 3).equals("Crown") && "skins:crown_test".equals(String.valueOf(slot(player, 3).get(DataComponents.ITEM_MODEL))),
				"the owned skin is " + name(player, 3));
		assertThat(slot(player, 4).isEmpty(), "the Dormant skin is listed: " + slot(player, 4));
		click(player, 0);
		assertThat(title(player).equals("Your Skins"), "clicking a ??? opened " + title(player));

		fx.skins.grant(player.nameAndId(), "candy_sword_test");
		fx.skins.grant(player.nameAndId(), "old_test");
		SkinMenu.open(player, fx.skins);
		assertThat(name(player, 0).equals("Zebra Hat") && name(player, 1).equals("???") && name(player, 2).equals("Candy Sword") && name(player, 3).equals("Crown"),
				"after claiming: " + name(player, 0) + ", " + name(player, 1) + ", " + name(player, 2) + ", " + name(player, 3));
		helper.succeed();
	}

	/**
	 * Skins are listed by the Skin Box they're in, boxes in id order (a skin in two boxes goes with the
	 * first), then the skins in no box; by name within each. "All Skins" lists them the same way.
	 */
	@GameTest
	public void skinsAreListedByTheirSkinBox(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().skin("a_loose", "helmet", "Aardvark Hat", null).skin("z_classic", "helmet", "Zebra Hat", null)
				.skin("m_classic", "helmet", "Moose Hat", null).skin("b_winter", "helmet", "Bear Hat", null).skin("both", "helmet", "Yak Hat", null)
				.box("classic.json5", "{ name: \"Classic\", skins: [ { skin: \"z_classic\", weight: 1 }, { skin: \"m_classic\", weight: 9 } ] }")
				.box("winter.json5", "{ name: \"Winter\", skins: [ { skin: \"b_winter\", weight: 1 }, { skin: \"both\", weight: 1 } ] }")
				.box("another.json5", "{ name: \"Another\", skins: [ { skin: \"both\", weight: 1 } ] }");
		fx.reload();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		for (String skin : List.of("a_loose", "z_classic", "m_classic", "b_winter", "both")) fx.skins.grant(player.nameAndId(), skin);
		List<String> expected = List.of("Yak Hat", "Moose Hat", "Zebra Hat", "Bear Hat", "Aardvark Hat");
		SkinMenu.open(player, fx.skins);
		List<String> listed = java.util.stream.IntStream.range(0, 5).mapToObj(i -> name(player, i)).toList();
		assertThat(listed.equals(expected), "Your Skins lists " + listed);
		SkinMenu.openAll(player, fx.skins);
		List<String> all = java.util.stream.IntStream.range(0, 5).mapToObj(i -> name(player, i)).toList();
		assertThat(all.equals(expected), "All Skins lists " + all);
		helper.succeed();
	}

	/**
	 * A Dyeable Hat with a saved tint shows in it, and its Apply screen has Clear tint, which puts it
	 * back in its starting colour (the button goes); a hat with no tint has no button.
	 */
	@GameTest
	public void clearTintIsOnTheApplyScreenOfATintedHat(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().dyeableHat("cowboy_hat_test");
		fx.reload();
		SkinFixtures.Listening player = SkinFixtures.join(helper);
		try {
			player.getInventory().clearContent();
			player.getInventory().setItem(0, new ItemStack(Items.DIAMOND_HELMET));
			fx.skins.grant(player.nameAndId(), "cowboy_hat_test");
			fx.skins.apply(player, "cowboy_hat_test", ItemLocation.hotbar(0));
			player.getInventory().getItem(0).set(DataComponents.DYED_COLOR, new DyedItemColor(0xB02E26));
			SkinMenu.open(player, fx.skins);   // the tidy saves the tint
			assertThat(new DyedItemColor(0xB02E26).equals(slot(player, 0).get(DataComponents.DYED_COLOR)), "the icon isn't tinted: " + slot(player, 0).getComponentsPatch());
			click(player, 0);
			assertThat(name(player, CLEAR_TINT).equals("Clear tint") && lore(player, CLEAR_TINT).getFirst().equals("Tint: #B02E26"),
					"slot 7: " + name(player, CLEAR_TINT) + " " + lore(player, CLEAR_TINT));
			click(player, CLEAR_TINT);
			assertThat(!player.getInventory().getItem(0).has(DataComponents.DYED_COLOR), "the helmet kept its colour");
			assertThat(title(player).startsWith("Apply: ") && slot(player, CLEAR_TINT).is(Items.STAINED_GLASS_PANE.gray()), "after clearing, slot 7 is " + slot(player, CLEAR_TINT));
		} finally {
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
		helper.succeed();
	}

	/**
	 * The Extract button opens "Extract: choose a skin": only the player's own skins, a skin on an item
	 * saying to remove it first and doing nothing when clicked, Back to "Your Skins". Clicking a skin
	 * opens its Extract screen, whose Cancel and Extract both come back to "Your Skins", where the
	 * extracted skin is a "???" again.
	 */
	@GameTest
	public void theExtractButtonPicksASkinThenConfirms(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().skin("crown_test", "helmet", "Crown", null).skin("candy_sword_test", "sword", "Candy Sword", null)
				.skin("frost", "sword", "Frost Blade", null);
		fx.reload();
		SkinFixtures.Listening player = SkinFixtures.join(helper);
		try {
			player.getInventory().clearContent();
			player.getInventory().setItem(0, new ItemStack(Items.DIAMOND_SWORD));
			for (String skin : List.of("crown_test", "candy_sword_test")) fx.skins.grant(player.nameAndId(), skin);
			fx.skins.apply(player, "candy_sword_test", ItemLocation.hotbar(0));
			SkinMenu.open(player, fx.skins);
			assertThat(name(player, EXTRACT).equals("Extract a skin") && lore(player, EXTRACT).equals(List.of("Turn a skin you own back into",
					"its Skin Token, to trade or give away")), "the Extract button: " + name(player, EXTRACT) + " " + lore(player, EXTRACT));

			click(player, EXTRACT);
			assertThat(title(player).equals("Extract: choose a skin") && rows(player) == 6, "the Extract button opened " + title(player));
			// Only their own, in the menus' order: Candy Sword, Crown; not Frost Blade.
			assertThat(name(player, 0).equals("Candy Sword") && name(player, 1).equals("Crown") && slot(player, 2).isEmpty(),
					"listed " + name(player, 0) + ", " + name(player, 1) + ", " + name(player, 2));
			assertThat(lore(player, 0).getLast().equals("Remove it from its item first") && lore(player, 1).getLast().equals("Click to extract"),
					"the skins say " + lore(player, 0) + " and " + lore(player, 1));
			click(player, 0);
			assertThat(title(player).equals("Extract: choose a skin"), "clicking the applied skin opened " + title(player));
			assertThat(name(player, BOOK).equals("Back to your skins"), "row 6 has no Back: " + name(player, BOOK));
			click(player, BOOK);
			assertThat(title(player).equals("Your Skins"), "Back went to " + title(player));

			click(player, EXTRACT);
			click(player, 1);
			assertThat(title(player).equals("Extract Crown?"), "clicking Crown opened " + title(player));
			click(player, 16);   // Cancel
			assertThat(title(player).equals("Your Skins") && fx.skins.ownedSkins(player.getUUID()).owns("crown_test"), "Cancel went to " + title(player));

			click(player, EXTRACT);
			click(player, 1);
			click(player, 10);   // Extract
			assertThat(title(player).equals("Your Skins"), "Extract went to " + title(player));
			assertThat(!fx.skins.ownedSkins(player.getUUID()).owns("crown_test") && SkinToken.skinOf(player.getInventory().getItem(1)).equals(Optional.of("crown_test")),
					"Crown wasn't extracted: " + player.getInventory().getItem(1));
			assertThat(name(player, 1).equals("???"), "the extracted Crown shows as " + name(player, 1));
		} finally {
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
		helper.succeed();
	}

	/** Whether the slot's name is drawn bold, wherever in the name the style sits. */
	private static boolean boldName(ServerPlayer player, int slot) {
		return slot(player, slot).getHoverName().visit((style, text) -> !text.isEmpty() && style.isBold() ? Optional.of(true) : Optional.empty(), Style.EMPTY)
				.isPresent();
	}

	@GameTest
	public void moreThanAChestOfSkinsIsPaged(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create();
		for (int i = 0; i < 46; i++) fx.skin("skin" + i, "hoe");
		fx.reload();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		for (int i = 0; i < 46; i++) fx.skins.grant(player.nameAndId(), "skin" + i);
		SkinMenu.open(player, fx.skins);
		assertThat(title(player).equals("Your Skins (1/2)"), "the first page is " + title(player));
		assertThat(!slot(player, 44).isEmpty() && slot(player, NEXT_PAGE).is(Items.ARROW) && !slot(player, PREVIOUS_PAGE).is(Items.ARROW), "the first page's arrows");
		click(player, NEXT_PAGE);
		assertThat(title(player).equals("Your Skins (2/2)"), "the next page is " + title(player));
		assertThat(!slot(player, 0).isEmpty() && slot(player, 1).isEmpty(), "the second page holds more than the 46th skin");
		assertThat(slot(player, PREVIOUS_PAGE).is(Items.ARROW) && !slot(player, NEXT_PAGE).is(Items.ARROW), "the last page's arrows");
		helper.succeed();
	}

	/** A player can carry 41 items a skin fits (every slot, worn and offhand too): past 27 the apply screen is paged. */
	@GameTest
	public void moreItemsThanTheApplyScreenHoldsArePaged(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().skin("candy_sword_test", "sword", "Candy Sword", null);
		fx.reload();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.getInventory().clearContent();
		for (int slot = 0; slot <= 40; slot++) player.getInventory().setItem(slot, new ItemStack(Items.IRON_SWORD));
		fx.skins.grant(player.nameAndId(), "candy_sword_test");
		SkinMenu.open(player, fx.skins);
		click(player, 0);

		assertThat(title(player).equals("Apply: Candy Sword (1/2)") && rows(player) == 4, "the first page is " + title(player));
		assertThat(!slot(player, FIRST_ITEM + 26).isEmpty() && slot(player, APPLY_NEXT_PAGE).is(Items.ARROW) && !slot(player, APPLY_PREVIOUS_PAGE).is(Items.ARROW),
				"the first page's arrows");
		click(player, APPLY_NEXT_PAGE);
		assertThat(title(player).equals("Apply: Candy Sword (2/2)"), "the next page is " + title(player));
		assertThat(lore(player, FIRST_ITEM).contains("Inventory") && lore(player, FIRST_ITEM + 13).contains("Offhand") && slot(player, FIRST_ITEM + 14).isEmpty(),
				"the second page holds the 28th to 41st items: " + lore(player, FIRST_ITEM) + ", " + lore(player, FIRST_ITEM + 13));
		assertThat(slot(player, APPLY_PREVIOUS_PAGE).is(Items.ARROW) && !slot(player, APPLY_NEXT_PAGE).is(Items.ARROW), "the last page's arrows");
		click(player, FIRST_ITEM + 13);
		assertThat(fx.skins.liveSkin(player.getItemBySlot(EquipmentSlot.OFFHAND)).isPresent(), "clicking the offhand sword on page 2 didn't skin it");
		helper.succeed();
	}

	@GameTest
	public void theBookSummarisesSkins(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create();
		fx.reload();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		SkinMenu.open(player, fx.skins);
		assertThat(lore(player, BOOK).equals(List.of("Claim a Skin Token to own a skin.", "Click a skin to put it on one item.",
				"A skin is on one item at a time.", "Extract turns a skin back into its token.", "Open a Skin Box to roll for a token.")), "the book reads " + lore(player, BOOK));
		click(player, BOOK);
		assertThat(title(player).equals("Your Skins"), "clicking the book left " + title(player));
		helper.succeed();
	}

	/**
	 * "All Skins" ({@code /itemskins list}): every skin, owned or not, in the menus' one order (a dated
	 * skin before undated ones), each with its Look, coloured name and release; a Dormant one last, as
	 * a barrier saying why; clicking does nothing.
	 */
	@GameTest
	public void allSkinsShowsEverySkin(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().skin("crown_test", "helmet", "Crown", null).skin("candy_sword_test", "sword", "Candy Sword", "Sweet.")
				.lookOnly("hat", "hat")
				.file("crown_test", "skin.json5", "{ name: \"&6&lCrown\", kind: \"helmet\", released: \"2026/10/31\", obtainable: false,"
						+ " look: { model: \"crown_test.json\" } }");
		fx.reload();
		SkinFixtures.Listening player = SkinFixtures.join(helper);
		try {
			fx.skins.grant(player.nameAndId(), "crown_test");
			SkinMenu.openAll(player, fx.skins);
			assertThat(title(player).equals("All Skins") && rows(player) == 6, "the menu is " + title(player) + " with " + rows(player) + " rows");
			assertThat(name(player, 0).equals("Crown") && name(player, 1).equals("Candy Sword") && name(player, 2).equals("hat") && slot(player, 3).isEmpty(),
					"listed " + name(player, 0) + ", " + name(player, 1) + ", " + name(player, 2));
			assertThat(lore(player, 1).equals(List.of("Fits any sword", "Obtainable", "Sweet.")), "the sword's tooltip: " + lore(player, 1));
			assertThat(lore(player, 0).equals(List.of("Fits any helmet", "Released: 2026/10/31 - Unobtainable")), "the crown's tooltip: " + lore(player, 0));
			assertThat(slot(player, 0).getHoverName().getSiblings().getFirst().getStyle().isBold(), "the crown's name isn't bold");
			assertThat("skins:crown_test".equals(String.valueOf(slot(player, 0).get(DataComponents.ITEM_MODEL))), "the crown's icon isn't the Look");
			assertThat(slot(player, 2).is(Items.BARRIER) && lore(player, 2).getFirst().startsWith("Dormant: ") && lore(player, 2).getFirst().contains("unknown kind"),
					"the Dormant hat: " + lore(player, 2));
			assertThat(name(player, BOOK).equals("How skins work"), "row 6 has no book: " + name(player, BOOK));
			click(player, 1);
			assertThat(title(player).equals("All Skins"), "clicking a skin opened " + title(player));
		} finally {
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
		helper.succeed();
	}

	/** Clicks a slot of the open menu as a client does: a left click, through the player's connection. */
	static void click(ServerPlayer player, int slot) {
		AbstractContainerMenu menu = player.containerMenu;
		player.connection.handleContainerClick(new ServerboundContainerClickPacket(menu.containerId, menu.getStateId(), (short) slot, (byte) 0,
				ContainerInput.PICKUP, Int2ObjectMaps.emptyMap(), HashedStack.EMPTY));
	}

	static String title(ServerPlayer player) {
		return player.containerMenu instanceof SlotBasedWrapperMenu menu ? menu.getBackingGui().getTitle().getString() : "no Skin Menu (" + player.containerMenu + ")";
	}

	private static int rows(ServerPlayer player) {
		return player.containerMenu instanceof SlotBasedWrapperMenu menu ? menu.getBackingGui().getHeight() : 0;
	}

	private static ItemStack slot(ServerPlayer player, int slot) {
		return player.containerMenu.getSlot(slot).getItem();
	}

	private static String name(ServerPlayer player, int slot) {
		return slot(player, slot).getHoverName().getString();
	}

	static List<String> lore(ServerPlayer player, int slot) {
		return slot(player, slot).getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().stream().map(Component::getString).toList();
	}
}
