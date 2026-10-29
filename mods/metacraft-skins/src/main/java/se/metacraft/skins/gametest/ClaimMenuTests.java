package se.metacraft.skins.gametest;

import se.metacraft.skins.item.SkinToken;
import se.metacraft.skins.ui.ClaimMenu;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;

import static se.metacraft.skins.gametest.SkinFixtures.assertThat;

/**
 * Wiring only: the claim chest, clicked the way a client clicks it. What a Claim does is
 * TokenTests'; the right-click that opens the chest is SkinsCommandTests'. The menu gets the
 * test's Skins directly, so nothing global is swapped.
 */
public final class ClaimMenuTests {
	private static final Set<Integer> CLAIM = Set.of(10, 11), CANCEL = Set.of(15, 16);
	private static final int SKIN = 13;

	@GameTest
	public void greenClaimsAndRedCancels(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().perky("crown_test", "helmet", "Crown", "Fit for a king.", PerkTests.HEARTS_WORN);
		fx.reload();
		SkinFixtures.Listening player = SkinFixtures.join(helper);
		try {
			player.getInventory().clearContent();
			player.getInventory().setItem(0, SkinToken.of("crown_test", 2));
			ItemStack token = player.getInventory().getItem(0);
			ClaimMenu.open(player, fx.skins, token);

			assertThat(SkinMenuTests.title(player).equals("Claim Crown?"), "the claim menu is " + SkinMenuTests.title(player));
			assertThat(player.containerMenu.slots.size() == 27 + 36, "the claim menu isn't 3 rows");
			for (int slot = 0; slot < 27; slot++) {
				ItemStack item = slot(player, slot);
				if (CLAIM.contains(slot)) {
					assertThat(item.is(Items.STAINED_GLASS_PANE.lime()) && name(player, slot).equals("✔ Claim Crown") && lore(player, slot).equals(List.of("The token is used up")),
							"claim pane " + slot + ": " + item + " " + name(player, slot) + " " + lore(player, slot));
				} else if (CANCEL.contains(slot)) {
					assertThat(item.is(Items.STAINED_GLASS_PANE.red()) && name(player, slot).equals("✖ Cancel") && lore(player, slot).equals(List.of("You keep the token")),
							"cancel pane " + slot + ": " + item + " " + name(player, slot) + " " + lore(player, slot));
				} else if (slot == SKIN) {
					assertThat("skins:crown_test".equals(String.valueOf(item.get(DataComponents.ITEM_MODEL))) && name(player, slot).equals("Crown"), "the skin in the centre: " + item);
					assertThat(lore(player, slot).equals(List.of("Fits any helmet", "Obtainable", "While worn: +2 max hearts", "Fit for a king.", "Claim to own this skin")),
							"the skin's tooltip: " + lore(player, slot));
				} else {
					assertThat(item.is(Items.STAINED_GLASS_PANE.black()), "slot " + slot + " isn't filler: " + item);
				}
			}

			SkinMenuTests.click(player, 16);
			assertThat(player.containerMenu == player.inventoryMenu, "cancel left " + SkinMenuTests.title(player) + " open");
			assertThat(player.heard.contains("Claim cancelled. You keep the token.") && token.getCount() == 2, "cancelling: " + player.heard + ", " + token);
			assertThat(!fx.skins.ownedSkins(player.getUUID()).owns("crown_test"), "cancelling claimed the skin");

			ClaimMenu.open(player, fx.skins, token);
			SkinMenuTests.click(player, 10);
			assertThat(player.containerMenu == player.inventoryMenu, "claiming left " + SkinMenuTests.title(player) + " open");
			assertThat(fx.skins.ownedSkins(player.getUUID()).owns("crown_test") && token.getCount() == 1, "claiming: owns " + fx.skins.ownedSkins(player.getUUID()) + ", " + token);
			String claimed = "✨ You now own Crown! Open /itemskins to apply it.";
			assertThat(player.heard.contains(claimed) && player.actionBar.contains(claimed), "the player was told " + player.heard);
		} finally {
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
		helper.succeed();
	}

	/** A limited skin in the centre is shown on an item it fits, and names its Materials in vanilla order. */
	@GameTest
	public void aLimitedSkinIsShownOnAnItemItFits(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().limited("picks", "pickaxe", "[\"iron\", \"wood\", \"stone\"]");
		fx.reload();
		SkinFixtures.Listening player = SkinFixtures.join(helper);
		try {
			player.getInventory().clearContent();
			player.getInventory().setItem(0, SkinToken.of("picks", 1));
			ClaimMenu.open(player, fx.skins, player.getInventory().getItem(0));
			assertThat(slot(player, SKIN).is(Items.IRON_PICKAXE), "the skin is shown on " + slot(player, SKIN));
			assertThat(lore(player, SKIN).getFirst().equals("Fits wooden, stone and iron pickaxes"), "the skin's tooltip: " + lore(player, SKIN));
		} finally {
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
		helper.succeed();
	}

	@GameTest
	public void anOwnedSkinsTokenIsKept(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().skin("crown_test", "helmet", "Crown", null);
		fx.reload();
		SkinFixtures.Listening player = SkinFixtures.join(helper);
		try {
			player.getInventory().clearContent();
			player.getInventory().setItem(0, SkinToken.of("crown_test", 1));
			ItemStack token = player.getInventory().getItem(0);
			fx.skins.grant(player.nameAndId(), "crown_test");
			ClaimMenu.open(player, fx.skins, token);

			assertThat(SkinMenuTests.title(player).equals("Already owned"), "the claim menu is " + SkinMenuTests.title(player));
			for (int slot : CLAIM) {
				assertThat(slot(player, slot).is(Items.STAINED_GLASS_PANE.gray()) && name(player, slot).equals("You already own Crown")
						&& lore(player, slot).equals(List.of("Keep the token to trade it")), "pane " + slot + ": " + name(player, slot) + " " + lore(player, slot));
			}
			assertThat(lore(player, SKIN).contains("Already yours"), "the skin's tooltip: " + lore(player, SKIN));
			SkinMenuTests.click(player, 11);
			assertThat(player.heard.getLast().equals("You already own Crown."), "the player was told " + player.heard);
			assertThat(token.getCount() == 1, "the token was used up");
			assertThat(SkinMenuTests.title(player).equals("Already owned"), "the refusal went to " + SkinMenuTests.title(player));
		} finally {
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
		helper.succeed();
	}

	@GameTest
	public void aClaimThatCantBeSavedSaysSoAndKeepsTheToken(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().skin("crown_test", "helmet", "Crown", null);
		fx.reload();
		SkinFixtures.Listening player = SkinFixtures.join(helper);
		try {
			player.getInventory().clearContent();
			player.getInventory().setItem(0, SkinToken.of("crown_test", 1));
			ItemStack token = player.getInventory().getItem(0);
			ClaimMenu.open(player, fx.skins, token);
			try {
				Files.createDirectories(fx.root.resolve("skins-data"));
				Files.writeString(fx.root.resolve("skins-data/players"), "not a folder");
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
			SkinMenuTests.click(player, 10);
			assertThat(player.heard.getLast().equals("Your skins couldn't be saved, so you keep the token. Try again later."), "the player was told " + player.heard);
			assertThat(token.getCount() == 1, "the token was used up");
		} finally {
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
		helper.succeed();
	}

	/** A Dyeable Hat in the centre says, under what it fits, that dyeing the helmet recolours it. */
	@GameTest
	public void aDyeableHatSaysDyeingRecoloursIt(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().dyeableHat("cowboy_hat_test");
		fx.reload();
		SkinFixtures.Listening player = SkinFixtures.join(helper);
		try {
			player.getInventory().clearContent();
			player.getInventory().setItem(0, SkinToken.of("cowboy_hat_test", 1));
			ClaimMenu.open(player, fx.skins, player.getInventory().getItem(0));
			assertThat(lore(player, SKIN).equals(List.of("Fits any helmet", "Dye the helmet to recolour it", "Obtainable", "Claim to own this skin")),
					"the skin's tooltip: " + lore(player, SKIN));
		} finally {
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
		helper.succeed();
	}

	private static ItemStack slot(SkinFixtures.Listening player, int slot) {
		return player.containerMenu.getSlot(slot).getItem();
	}

	private static String name(SkinFixtures.Listening player, int slot) {
		return slot(player, slot).getHoverName().getString();
	}

	private static List<String> lore(SkinFixtures.Listening player, int slot) {
		return slot(player, slot).getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().stream().map(Component::getString).toList();
	}
}
