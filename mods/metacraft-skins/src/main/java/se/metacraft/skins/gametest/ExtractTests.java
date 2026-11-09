package se.metacraft.skins.gametest;

import se.metacraft.skins.ItemLocation;
import se.metacraft.skins.OwnedSkin;
import se.metacraft.skins.OwnershipChange;
import se.metacraft.skins.Skins;
import se.metacraft.skins.item.SkinToken;
import se.metacraft.skins.ui.ExtractMenu;
import se.metacraft.skins.ui.SkinMenu;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.HashedStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static se.metacraft.skins.gametest.SkinFixtures.assertThat;

/**
 * Extract: an Owner turns a skin they own back into its Skin Token, through {@link Skins#extract}.
 * The command and the Extract screen that call it are tested here too, the screen clicked the way a
 * client clicks it.
 */
public final class ExtractTests {
	@GameTest
	public void extractingAnUnappliedSkinGivesItsTokenBack(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().skin("crown_test", "helmet", "Crown", null);
		fx.reload();
		ServerPlayer player = ApplyTests.carrying(helper);
		fx.skins.grant(player.nameAndId(), "crown_test");

		Skins.Extracted extracted = fx.skins.extract(player, "crown_test");
		assertThat(extracted.result() == OwnershipChange.DONE && !extracted.dropped(), "the extract: " + extracted);
		assertThat(!fx.restart().ownedSkins(player.getUUID()).owns("crown_test"), "the file still has the skin");
		ItemStack token = player.getInventory().getItem(0);
		assertThat(SkinToken.skinOf(token).equals(Optional.of("crown_test")) && token.getCount() == 1, "the player got " + token);
		ItemStack seen = TokenTests.view(helper, fx, token, player);
		var lore = seen.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().stream().map(Component::getString).toList();
		assertThat(lore.getLast().equals("Right-click to claim"), "the token reads, to them: " + lore);
		helper.succeed();
	}

	@GameTest
	public void aFullInventoryDropsTheTokenAtTheirFeetForThemAlone(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().skin("crown_test", "helmet");
		fx.reload();
		ServerPlayer player = full(helper);
		fx.skins.grant(player.nameAndId(), "crown_test");

		Skins.Extracted extracted = fx.skins.extract(player, "crown_test");
		assertThat(extracted.result() == OwnershipChange.DONE && extracted.dropped(), "the extract: " + extracted);
		List<? extends ItemEntity> dropped = helper.getLevel().getEntities(EntityTypes.ITEM, e -> SkinToken.skinOf(e.getItem()).equals(Optional.of("crown_test")));
		assertThat(dropped.size() == 1 && dropped.getFirst().getItem().getCount() == 1, "dropped " + dropped);
		ItemEntity drop = dropped.getFirst();
		assertThat(drop.distanceTo(player) < 2, "dropped at " + drop.position() + ", not the feet of a player at " + player.position());
		assertThat(!drop.hasPickUpDelay(), "the drop has a pickup delay");
		ServerPlayer someoneElse = ApplyTests.carrying(helper);
		drop.playerTouch(someoneElse);
		assertThat(drop.isAlive() && someoneElse.getInventory().isEmpty(), "someone else picked up the token");
		player.getInventory().setItem(0, ItemStack.EMPTY);
		drop.playerTouch(player);
		assertThat(!drop.isAlive() && SkinToken.skinOf(player.getInventory().getItem(0)).isPresent(), "the player couldn't pick up their token");
		helper.succeed();
	}

	/** Every refusal changes nothing: the skin stays owned (as it was), and no token is given. */
	@GameTest
	public void anAppliedDormantOrUnownedSkinIsRefused(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().skin("crown_test", "helmet").skin("broken", "sword").skin("free", "sword");
		fx.reload();
		ServerPlayer player = ApplyTests.carrying(helper, new ItemStack(Items.IRON_HELMET));
		fx.skins.grant(player.nameAndId(), "crown_test");
		fx.skins.grant(player.nameAndId(), "broken");
		assertThat(fx.skins.apply(player, "crown_test", ItemLocation.hotbar(0)).applied(), "the apply was refused");
		fx.file("broken", "skin.json5", "{ name: \"Broken\", kind: \"nope\" }");
		fx.reload();

		// Working (on them), then Away (gone from them): applied in this world either way.
		assertRefused(fx, player, "crown_test", OwnershipChange.STILL_APPLIED);
		player.getInventory().clearContent();
		assertRefused(fx, player, "crown_test", OwnershipChange.STILL_APPLIED);
		assertThat(fx.skins.ownedSkins(player.getUUID()).skins().stream().anyMatch(s -> s.id().equals("crown_test") && s.state() == OwnedSkin.State.APPLIED),
				"a refused extract unapplied it: " + fx.skins.ownedSkins(player.getUUID()));
		assertRefused(fx, player, "broken", OwnershipChange.DORMANT_SKIN);
		assertRefused(fx, player, "free", OwnershipChange.NOT_OWNED);
		assertRefused(fx, player, "nowhere", OwnershipChange.NOT_OWNED);
		assertThat(fx.restart().ownedSkins(player.getUUID()).owns("crown_test") && fx.skins.ownedSkins(player.getUUID()).owns("broken"),
				"a refused extract deleted a skin");

		// Removed, the skin whose item is lost can be extracted.
		assertThat(fx.skins.remove(player.nameAndId(), "crown_test") == OwnershipChange.DONE, "the remove was refused");
		assertThat(fx.skins.extract(player, "crown_test").result() == OwnershipChange.DONE, "a removed skin couldn't be extracted");
		helper.succeed();
	}

	@GameTest
	public void aDamagedFileOrAFailedSaveKeepsTheSkinAndGivesNothing(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().skin("crown_test", "helmet");
		fx.reload();
		ServerPlayer damaged = ApplyTests.carrying(helper);
		String broken = "{ \"uuid\": \"" + damaged.getUUID() + "\", \"skins\": ";
		fx.playerFile(damaged.getUUID(), broken);
		assertRefused(fx, damaged, "crown_test", OwnershipChange.DAMAGED_FILE);
		assertThat(SkinFixtures.read(fx.playerFile(damaged.getUUID())).equals(broken), "the damaged file was overwritten");

		ServerPlayer player = ApplyTests.carrying(helper);
		fx.skins.grant(player.nameAndId(), "crown_test");
		try {
			// The players folder swapped for a file: nothing can be written there, but what's in memory still reads.
			Path players = fx.root.resolve("skins-data/players");
			Path aside = fx.root.resolve("skins-data/aside");
			Files.move(players, aside);
			Files.writeString(players, "not a folder");
			assertThat(fx.skins.extract(player, "crown_test").result() == OwnershipChange.SAVE_FAILED, "an extract that couldn't be saved didn't say so");
			assertThat(player.getInventory().isEmpty(), "an extract that couldn't be saved gave " + player.getInventory().getItem(0));
			assertThat(fx.skins.ownedSkins(player.getUUID()).owns("crown_test"), "an extract that couldn't be saved took the skin");
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		helper.succeed();
	}

	/**
	 * An unobtainable skin and a granted one extract like any other, and so does one applied in another
	 * world (it can't be removed here): its item there is plain for good. Claiming the token gives a fresh date.
	 */
	@GameTest
	public void anyUnappliedSkinExtractsAndItsTokenClaimsAfresh(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().skin("crown_test", "helmet")
				.file("rare", "skin.json5", "{ name: \"Rare\", kind: \"sword\", obtainable: false, look: { model: \"rare.json\" } }")
				.file("rare", "rare.json", SkinFixtures.model("rare")).png("rare", "rare");
		Path home = fx.root.resolve("home");
		Skins atHome = fx.restart(home);
		ServerPlayer player = ApplyTests.carrying(helper, new ItemStack(Items.IRON_HELMET));
		atHome.grant(player.nameAndId(), "crown_test");
		atHome.grant(player.nameAndId(), "rare");
		assertThat(atHome.apply(player, "crown_test", ItemLocation.hotbar(0)).applied(), "the apply at home was refused");
		ItemStack helmet = player.getInventory().getItem(0);
		player.getInventory().clearContent();

		Skins elsewhere = fx.restart(fx.root.resolve("elsewhere"));
		assertThat(elsewhere.extract(player, "rare").result() == OwnershipChange.DONE, "an unobtainable skin couldn't be extracted");
		assertThat(elsewhere.extract(player, "crown_test").result() == OwnershipChange.DONE, "a skin applied in another world couldn't be extracted");
		assertThat(fx.restart(home).liveSkin(helmet).isEmpty(), "the item in the other world still wears the extracted skin");

		ItemStack token = player.getInventory().getItem(1);
		assertThat(SkinToken.skinOf(token).equals(Optional.of("crown_test")), "the second token is " + token);
		Instant before = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		assertThat(elsewhere.claim(player, token) == OwnershipChange.DONE, "the extracted token couldn't be claimed");
		OwnedSkin claimed = elsewhere.ownedSkins(player.getUUID()).skins().getFirst();
		assertThat(!claimed.claimed().isBefore(before) && claimed.state() == OwnedSkin.State.UNAPPLIED, "claimed again: " + claimed);
		helper.succeed();
	}

	@GameTest
	public void theExtractScreenShowsTheTokenAndOnlyExtractChangesAnything(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().perky("crown_test", "helmet", "&6Crown", "Fit for a king.", PerkTests.HEARTS_WORN);
		fx.reload();
		SkinFixtures.Listening player = SkinFixtures.join(helper);
		try {
			player.getInventory().clearContent();
			fx.skins.grant(player.nameAndId(), "crown_test");
			ExtractMenu.open(player, fx.skins, "crown_test", () -> SkinMenu.open(player, fx.skins));

			assertThat(SkinMenuTests.title(player).equals("Extract Crown?"), "the screen is " + SkinMenuTests.title(player));
			assertThat(player.containerMenu.slots.size() == 27 + 36, "the screen isn't 3 rows");
			for (int slot = 0; slot < 27; slot++) {
				ItemStack item = slot(player, slot);
				if (EXTRACT.contains(slot)) {
					assertThat(item.is(Items.STAINED_GLASS_PANE.lime()) && name(player, slot).equals("✔ Extract Crown") && lore(player, slot).equals(List.of("You get its Skin Token back")),
							"extract pane " + slot + ": " + item + " " + name(player, slot) + " " + lore(player, slot));
				} else if (CANCEL.contains(slot)) {
					assertThat(item.is(Items.STAINED_GLASS_PANE.red()) && name(player, slot).equals("✖ Cancel") && lore(player, slot).equals(List.of("You keep the skin")),
							"cancel pane " + slot + ": " + item + " " + name(player, slot) + " " + lore(player, slot));
				} else if (slot == TOKEN) {
					assertThat(SkinToken.MODEL.equals(item.get(DataComponents.ITEM_MODEL)) && name(player, slot).equals("Skin Token: Crown"), "the token in the centre: " + item
							+ " named " + name(player, slot));
					assertThat(lore(player, slot).equals(List.of("Fits any helmet", "While worn: +2 max hearts", "Fit for a king.", "", "Extract to trade this skin as a token")),
							"the token's tooltip: " + lore(player, slot));
				} else {
					assertThat(item.is(Items.STAINED_GLASS_PANE.black()), "slot " + slot + " isn't filler: " + item);
				}
			}

			SkinMenuTests.click(player, 16);
			assertThat(SkinMenuTests.title(player).equals("Your Skins"), "cancel went to " + SkinMenuTests.title(player));
			assertThat(player.heard.getLast().equals("Extract cancelled. You keep the skin."), "cancelling: " + player.heard);
			assertThat(fx.skins.ownedSkins(player.getUUID()).owns("crown_test") && player.getInventory().isEmpty(), "cancelling extracted the skin");

			ExtractMenu.open(player, fx.skins, "crown_test", () -> SkinMenu.open(player, fx.skins));
			int heard = player.heard.size();
			player.connection.handleContainerClose(new ServerboundContainerClosePacket(player.containerMenu.containerId));
			assertThat(player.heard.size() == heard && fx.skins.ownedSkins(player.getUUID()).owns("crown_test"), "Escape: " + player.heard);

			// A double-click arrives as two clicks, the second a pickup-all: it extracts once.
			ExtractMenu.open(player, fx.skins, "crown_test", () -> SkinMenu.open(player, fx.skins));
			AbstractContainerMenu screen = player.containerMenu;
			player.sent(Object.class);
			SkinMenuTests.click(player, 10);
			player.connection.handleContainerClick(new ServerboundContainerClickPacket(screen.containerId, screen.getStateId(), (short) 10, (byte) 0,
					ContainerInput.PICKUP_ALL, Int2ObjectMaps.emptyMap(), HashedStack.EMPTY));
			assertThat(SkinMenuTests.title(player).equals("Your Skins"), "extracting went to " + SkinMenuTests.title(player));
			assertThat(!fx.skins.ownedSkins(player.getUUID()).owns("crown_test"), "Extract didn't extract");
			ItemStack token = player.getInventory().getItem(0);
			assertThat(SkinToken.skinOf(token).equals(Optional.of("crown_test")) && token.getCount() == 1 && player.getInventory().getItem(1).isEmpty(),
					"the player got " + token + " and " + player.getInventory().getItem(1));
			assertThat(player.heard.getLast().equals("You extracted Crown. Its Skin Token is in your inventory."), "the player was told " + player.heard);
			assertThat(player.sent(ClientboundSoundPacket.class).stream().anyMatch(s -> s.getSound().value().equals(SoundEvents.ITEM_PICKUP)), "no pickup sound");
		} finally {
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
		helper.succeed();
	}

	/** Checked again on Extract: a skin revoked while the screen was open is refused, and a full inventory is said. */
	@GameTest
	public void theScreenChecksAgainOnExtract(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().skin("crown_test", "helmet", "Crown", null);
		fx.reload();
		SkinFixtures.Listening player = SkinFixtures.join(helper);
		try {
			player.getInventory().clearContent();
			fx.skins.grant(player.nameAndId(), "crown_test");
			ExtractMenu.open(player, fx.skins, "crown_test", () -> SkinMenu.open(player, fx.skins));
			fx.skins.revoke(player.nameAndId(), "crown_test");
			SkinMenuTests.click(player, 11);
			assertThat(SkinMenuTests.title(player).equals("Your Skins"), "a refused extract went to " + SkinMenuTests.title(player));
			assertThat(player.heard.getLast().equals("You can't extract Crown right now, so nothing changed."), "the player was told " + player.heard);
			assertThat(player.getInventory().isEmpty(), "a refused extract gave " + player.getInventory().getItem(0));

			fx.skins.grant(player.nameAndId(), "crown_test");
			player.setPos(helper.absoluteVec(new Vec3(1.5, 1, 1.5)));
			player.setGameMode(GameType.SURVIVAL);
			for (int slot = 0; slot < 36; slot++) player.getInventory().setItem(slot, new ItemStack(Items.DIRT, 64));
			player.getInventory().setItem(Inventory.SLOT_OFFHAND, new ItemStack(Items.DIRT, 64));
			ExtractMenu.open(player, fx.skins, "crown_test", () -> SkinMenu.open(player, fx.skins));
			SkinMenuTests.click(player, 10);
			assertThat(player.heard.getLast().equals("You extracted Crown. Its Skin Token is at your feet: your inventory is full."), "the player was told " + player.heard);
			helper.getLevel().getEntities(EntityTypes.ITEM, e -> SkinToken.skinOf(e.getItem()).isPresent()).forEach(ItemEntity::discard);
		} finally {
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
		helper.succeed();
	}

	private static final Set<Integer> EXTRACT = Set.of(10, 11), CANCEL = Set.of(15, 16);
	private static final int TOKEN = 13;

	private static ItemStack slot(ServerPlayer player, int slot) {
		return player.containerMenu.getSlot(slot).getItem();
	}

	private static String name(ServerPlayer player, int slot) {
		return slot(player, slot).getHoverName().getString();
	}

	private static List<String> lore(ServerPlayer player, int slot) {
		return slot(player, slot).getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().stream().map(Component::getString).toList();
	}

	private static void assertRefused(SkinFixtures fx, ServerPlayer player, String skinId, OwnershipChange expected) {
		ItemStack[] before = new ItemStack[player.getInventory().getContainerSize()];
		for (int slot = 0; slot < before.length; slot++) before[slot] = player.getInventory().getItem(slot).copy();
		OwnershipChange dryRun = fx.skins.extractable(player, skinId);
		Skins.Extracted extracted = fx.skins.extract(player, skinId);
		assertThat(dryRun == expected && extracted.result() == expected, "extracting " + skinId + ": " + dryRun + ", then " + extracted + ", not " + expected);
		for (int slot = 0; slot < before.length; slot++) {
			assertThat(ItemStack.matches(before[slot], player.getInventory().getItem(slot)), "a refused extract changed slot " + slot + ": " + player.getInventory().getItem(slot));
		}
	}

	/** A survival player (creative swallows what doesn't fit) standing in the test, with no room left. */
	static ServerPlayer full(GameTestHelper helper) {
		ServerPlayer player = ApplyTests.carrying(helper);
		player.setPos(helper.absoluteVec(new Vec3(1.5, 1, 1.5)));
		player.setGameMode(GameType.SURVIVAL);
		for (int slot = 0; slot < 36; slot++) player.getInventory().setItem(slot, new ItemStack(Items.DIRT, 64));
		player.getInventory().setItem(Inventory.SLOT_OFFHAND, new ItemStack(Items.DIRT, 64));
		return player;
	}
}
