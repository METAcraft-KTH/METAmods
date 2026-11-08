package se.metacraft.skins.gametest;

import eu.pb4.sgui.api.SguiUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import se.metacraft.skins.BoxResult;
import se.metacraft.skins.BoxSummary;
import se.metacraft.skins.Skins;
import se.metacraft.skins.item.SkinBox;
import se.metacraft.skins.item.SkinToken;
import se.metacraft.skins.ui.Reel;
import se.metacraft.skins.ui.RollMenu;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.HashedStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static se.metacraft.skins.gametest.SkinFixtures.assertThat;

/**
 * Rolling a Skin Box: {@link Skins#roll} (what it checks, the box it uses up, the draw), the reel's
 * schedule and tape, and the Roll menu, clicked the way a client clicks it.
 */
public final class RollMenuTests {
	/** A box of two skins, "a" three times as likely as "b". */
	private static final String AB = "{ name: \"AB\", skins: [ { skin: \"a\", weight: 3 }, { skin: \"b\", weight: 1 } ] }";

	@GameTest
	public void aRollUsesUpOneBoxAndDrawsOnlyUsableSkins(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().skin("a", "sword").skin("b", "sword").box("ab.json5", AB);
		fx.file("b", "skin.json5", "{ name: \"B\", kind: \"nope\" }");
		fx.reload();
		ServerPlayer player = ApplyTests.carrying(helper, SkinBox.of("ab"), SkinBox.of("ab"), SkinBox.of("ab"));
		for (int slot = 0; slot < 3; slot++) {
			player.getInventory().setSelectedSlot(slot);
			Skins.Rolled rolled = fx.skins.roll(player, "ab");
			assertThat(rolled.result() == BoxResult.DONE && rolled.skin().equals(Optional.of("a")), "the roll: " + rolled);
			assertThat(rolled.entries().stream().map(BoxSummary.Entry::skin).toList().equals(List.of("a")), "rolled from " + rolled.entries());
			assertThat(player.getMainHandItem().isEmpty() && boxes(player) == 2 - slot, "the roll left " + player.getMainHandItem() + " and " + boxes(player) + " boxes");
		}
		helper.succeed();
	}

	/** Checked in this order, each keeping the box: it's still in the main hand, it isn't Dormant, it has something to roll. */
	@GameTest
	public void aRollThatCantGoAheadKeepsTheBox(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().skin("a", "sword").skin("b", "sword").box("ab.json5", AB);
		fx.reload();
		ServerPlayer player = ApplyTests.carrying(helper, new ItemStack(Items.DIAMOND), SkinBox.of("ab"));
		assertRefused(fx, player, "ab", BoxResult.BOX_GONE);   // it's in their inventory, not their hand
		player.getInventory().setItem(0, SkinBox.of("other"));
		assertRefused(fx, player, "ab", BoxResult.BOX_GONE);
		player.getInventory().setItem(0, SkinBox.of("ab"));

		fx.file("a", "skin.json5", "{ name: \"A\", kind: \"nope\" }").file("b", "skin.json5", "{ name: \"B\", kind: \"nope\" }");
		fx.reload();
		assertRefused(fx, player, "ab", BoxResult.NOTHING_TO_ROLL);
		fx.box("ab.json5", "{ name: \"AB\" }");
		fx.reload();
		assertRefused(fx, player, "ab", BoxResult.DORMANT_BOX);
		assertRefused(fx, player, "nowhere", BoxResult.BOX_GONE);
		player.getInventory().setItem(0, SkinBox.of("nowhere"));
		assertRefused(fx, player, "nowhere", BoxResult.DORMANT_BOX);
		helper.succeed();
	}

	/** The draw goes by weight: "a" (weight 3) about three times as often as "b" (weight 1). */
	@GameTest
	public void theDrawGoesByWeight(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().skin("a", "sword").skin("b", "sword").box("ab.json5", AB);
		fx.reload();
		List<BoxSummary.Entry> entries = fx.skins.box("ab").orElseThrow().entries();
		RandomSource random = RandomSource.create(20261104L);
		int a = 0;
		for (int i = 0; i < 4000; i++) if (BoxSummary.draw(entries, random).equals("a")) a++;
		assertThat(a > 2850 && a < 3150, "\"a\" came up " + a + " times in 4000, not about 3000");
		helper.succeed();
	}

	/** A box as heavy as the rules allow (thousands of skins at weight 1,000,000) still draws: the weights add up past an int. */
	@GameTest
	public void theHeaviestBoxStillDraws(GameTestHelper helper) {
		List<BoxSummary.Entry> entries = new ArrayList<>();
		for (int i = 0; i < 3000; i++) entries.add(new BoxSummary.Entry("s" + i, 1_000_000, "<0.1%"));
		RandomSource random = RandomSource.create(1);
		for (int i = 0; i < 100; i++) {
			String drawn = BoxSummary.draw(entries, random);
			assertThat(drawn.matches("s\\d+"), "drew " + drawn);
		}
		helper.succeed();
	}

	/** 40 steps over 100 ticks, slowing down: about one a tick at first, the last 16 ticks after the one before. */
	@GameTest
	public void theReelSlowsDownOverFiveSeconds(GameTestHelper helper) {
		int[] ticks = Reel.schedule();
		assertThat(ticks.length == 40 && ticks[0] == 1 && ticks[39] == 100, "the schedule: " + java.util.Arrays.toString(ticks));
		for (int k = 1; k < ticks.length; k++) assertThat(ticks[k] > ticks[k - 1], "step " + k + " isn't after the one before: " + java.util.Arrays.toString(ticks));
		assertThat(ticks[39] - ticks[38] == 16, "the last step comes " + (ticks[39] - ticks[38]) + " ticks after the one before");

		assertThat(Reel.pitch(0) == 0.5f && Reel.pitch(20) == 1f && Reel.pitch(40) == 2f, "the pitch: " + Reel.pitch(0) + ", " + Reel.pitch(20) + ", " + Reel.pitch(40));
		for (int s = 1; s <= 40; s++) assertThat(Reel.pitch(s) >= Reel.pitch(s - 1), "the pitch falls at step " + s);
		helper.succeed();
	}

	/** The tape: 40 fresh draws with the result at index 4, then the 9 skins the reel showed before the press. */
	@GameTest
	public void theTapeEndsOnTheResultAfterThePreview(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().skin("a", "sword").skin("b", "sword").box("ab.json5", AB);
		fx.reload();
		List<String> preview = List.of("b", "b", "a", "b", "a", "b", "b", "a", "b");
		List<String> tape = Reel.tape("b", preview, fx.skins.box("ab").orElseThrow().entries(), RandomSource.create(1));
		assertThat(tape.size() == 49 && tape.get(4).equals("b") && tape.subList(40, 49).equals(preview), "the tape: " + tape);
		assertThat(Set.of("a", "b").containsAll(new ArrayList<>(tape)), "the tape holds " + tape);
		helper.succeed();
	}

	// ---- the menu

	/** Two skins with descriptions: "a" (Alpha, a sword, 75%) and "b" (Beta, a helmet, 25%), in box "ab" named "AB". */
	private static SkinFixtures alphaBeta() {
		SkinFixtures fx = SkinFixtures.create().skin("a", "sword", "Alpha", "First.").skin("b", "helmet", "Beta", "Second.").box("ab.json5", AB);
		fx.reload();
		return fx;
	}

	@GameTest
	public void theReadyMenuPreviewsTheBoxAndLeavingIsFree(GameTestHelper helper) {
		SkinFixtures fx = alphaBeta();
		SkinFixtures.Listening player = holding(helper, "ab", 2);
		try {
			RollMenu.open(player, fx.skins, "ab");
			assertThat(SkinMenuTests.title(player).equals("AB") && player.containerMenu.slots.size() == 27 + 36, "the menu is " + SkinMenuTests.title(player));
			for (int slot = 0; slot < 27; slot++) {
				ItemStack item = slot(player, slot);
				if (slot >= REEL && slot < REEL + 9) {
					assertPreviewSkin(player, slot);
				} else if (slot == TOP) {
					assertThat(item.is(Items.STAINED_GLASS_PANE.orange()) && name(player, slot).equals("▼"), "the top marker: " + item + " " + name(player, slot));
				} else if (slot == BOTTOM) {
					assertThat(item.is(Items.VAULT) && item.has(DataComponents.CUSTOM_MODEL_DATA)
							&& item.get(DataComponents.CUSTOM_MODEL_DATA).strings().equals(List.of(SkinBox.LOOK)) && name(player, slot).equals("Roll") && lore(player, slot).equals(List.of("Uses up the box")),
							"the Roll button: " + item + " " + name(player, slot) + " " + lore(player, slot));
				} else {
					assertThat(item.is(Items.STAINED_GLASS_PANE.gray()), "slot " + slot + " isn't filler: " + item);
				}
			}
			player.connection.handleContainerClose(new ServerboundContainerClosePacket(player.containerMenu.containerId));
			assertThat(boxes(player) == 2 && tokens(player).isEmpty() && player.heard.stream().noneMatch(l -> l.startsWith("You rolled")),
					"leaving before Roll: " + boxes(player) + " boxes, " + tokens(player) + ", " + player.heard);
		} finally {
			leave(helper, player);
		}
		helper.succeed();
	}

	@GameTest
	public void aBoxWithNothingToRollKeepsTheBox(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().box("gone.json5", "{ name: \"Gone\", skins: [ { skin: \"nowhere\", weight: 1 } ] }");
		fx.reload();
		SkinFixtures.Listening player = holding(helper, "gone", 1);
		try {
			RollMenu.open(player, fx.skins, "gone");
			for (int slot = REEL; slot < REEL + 9; slot++) assertThat(slot(player, slot).is(Items.STAINED_GLASS_PANE.gray()), "reel slot " + slot + ": " + slot(player, slot));
			assertThat(slot(player, BOTTOM).is(Items.BARRIER) && name(player, BOTTOM).equals("Nothing to roll")
					&& lore(player, BOTTOM).equals(List.of("Every skin in this box is unavailable. Your box is kept.")), "the barrier: " + name(player, BOTTOM) + " " + lore(player, BOTTOM));
			// A double-click is two clicks, the second a pickup-all: it's refused once.
			AbstractContainerMenu menu = player.containerMenu;
			SkinMenuTests.click(player, BOTTOM);
			player.connection.handleContainerClick(new ServerboundContainerClickPacket(menu.containerId, menu.getStateId(), (short) BOTTOM, (byte) 0,
					ContainerInput.PICKUP_ALL, Int2ObjectMaps.emptyMap(), HashedStack.EMPTY));
			assertThat(player.actionBar.equals(List.of("This box has nothing to roll. Your box is kept.")), "the player was told " + player.actionBar);
			assertThat(player.getMainHandItem().getCount() == 1 && SkinMenuTests.title(player).equals("Gone"), "the barrier used the box or closed the menu");
		} finally {
			leave(helper, player);
		}
		helper.succeed();
	}

	/** Roll uses up one box (a double-click too) and spins for 100 ticks, clicking higher each step; it lands, gives one token, and stays open. */
	@GameTest(maxTicks = 400)
	public void pressingRollSpinsLandsAndGivesOneToken(GameTestHelper helper) {
		SkinFixtures fx = alphaBeta();
		SkinFixtures.Listening player = holding(helper, "ab", 3);
		SkinFixtures.Listening bystander = holding(helper, "ab", 1);
		RollMenu.open(player, fx.skins, "ab");
		AbstractContainerMenu menu = player.containerMenu;
		player.sent(Object.class);
		bystander.sent(Object.class);
		SkinMenuTests.click(player, BOTTOM);
		player.connection.handleContainerClick(new ServerboundContainerClickPacket(menu.containerId, menu.getStateId(), (short) BOTTOM, (byte) 0,
				ContainerInput.PICKUP_ALL, Int2ObjectMaps.emptyMap(), HashedStack.EMPTY));
		SkinMenuTests.click(player, BOTTOM);
		assertThat(player.getMainHandItem().isEmpty() && boxes(player) == 2, "Roll left " + player.getMainHandItem() + " and " + boxes(player) + " boxes");
		assertThat(slot(player, BOTTOM).is(Items.STAINED_GLASS_PANE.orange()) && name(player, BOTTOM).equals("▲"), "the bottom marker while rolling: " + name(player, BOTTOM));
		ticking(helper, player);
		helper.runAfterDelay(Reel.TICKS + 5, () -> {
			try {
				List<ClientboundSoundPacket> sounds = player.sent(ClientboundSoundPacket.class);
				List<ClientboundSoundPacket> clicks = sounds.stream().filter(s -> s.getSound().value().equals(SoundEvents.NOTE_BLOCK_HAT.value())).toList();
				assertThat(clicks.size() == Reel.STEPS && clicks.getFirst().getPitch() == 0.5f && clicks.getFirst().getVolume() == 0.6f,
						clicks.size() + " clicks, the first at pitch " + (clicks.isEmpty() ? "-" : clicks.getFirst().getPitch()));
				for (int i = 1; i < clicks.size(); i++) assertThat(clicks.get(i).getPitch() >= clicks.get(i - 1).getPitch(), "the clicks fall at " + i);
				assertThat(sounds.getLast().getSound().value().equals(SoundEvents.PLAYER_LEVELUP) && sounds.stream().filter(s -> s.getSound().value()
						.equals(SoundEvents.PLAYER_LEVELUP)).count() == 1, "the landing sound: " + sounds.getLast().getSound());
				assertThat(bystander.sent(ClientboundSoundPacket.class).stream().noneMatch(s -> s.getSound().value().equals(SoundEvents.NOTE_BLOCK_HAT.value())
						|| s.getSound().value().equals(SoundEvents.PLAYER_LEVELUP)), "someone else heard the roll");

				List<ItemStack> tokens = tokens(player);
				assertThat(tokens.size() == 1 && tokens.getFirst().getCount() == 1, "the player got " + tokens);
				String result = SkinToken.skinOf(tokens.getFirst()).orElseThrow();
				String name = result.equals("a") ? "Alpha" : "Beta";
				assertThat(player.heard.stream().filter(l -> l.startsWith("You rolled")).toList().equals(List.of("You rolled " + name + "!")), "the player was told " + player.heard);
				assertThat(player.actionBar.isEmpty(), "the roll went to the action bar: " + player.actionBar);

				assertThat(name(player, CENTRE).equals(name) && Boolean.TRUE.equals(slot(player, CENTRE).get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)),
						"the centre: " + name(player, CENTRE) + " " + slot(player, CENTRE));
				List<String> full = result.equals("a") ? List.of("75% chance", "Fits any sword", "Obtainable", "First.") : List.of("25% chance", "Fits any helmet", "Obtainable", "Second.");
				assertThat(lore(player, CENTRE).equals(full), "the result's tooltip: " + lore(player, CENTRE));
				for (int slot = REEL; slot < REEL + 9; slot++) if (slot != CENTRE) assertPreviewSkin(player, slot);
				assertThat(slot(player, TOP).is(Items.STAINED_GLASS_PANE.lime()) && name(player, TOP).equals("▼ " + name)
						&& slot(player, BOTTOM).is(Items.STAINED_GLASS_PANE.lime()) && name(player, BOTTOM).equals("▲ " + name),
						"the markers: " + name(player, TOP) + ", " + name(player, BOTTOM));

				// It stays open, offers no Roll, and closing it gives nothing more.
				SkinMenuTests.click(player, BOTTOM);
				assertThat(SkinMenuTests.title(player).equals("AB") && boxes(player) == 2, "the landed menu rolled again or closed");
				player.closeContainer();
				assertThat(tokens(player).equals(tokens) && tokens.getFirst().getCount() == 1, "closing the landed menu gave " + tokens(player));
			} finally {
				leave(helper, player);
				leave(helper, bystander);
			}
			helper.succeed();
		});
	}

	/** Escape, another menu opening over it, and a code close() each give the one token at once, with no landing sound. */
	@GameTest
	public void closingMidSpinGivesTheTokenAtOnce(GameTestHelper helper) {
		SkinFixtures fx = alphaBeta();
		List<SkinFixtures.Listening> players = List.of(holding(helper, "ab", 1), holding(helper, "ab", 1), holding(helper, "ab", 1));
		try {
			for (SkinFixtures.Listening player : players) {
				RollMenu.open(player, fx.skins, "ab");
				SkinMenuTests.click(player, BOTTOM);
				player.sent(Object.class);
			}
			players.get(0).connection.handleContainerClose(new ServerboundContainerClosePacket(players.get(0).containerMenu.containerId));
			players.get(1).openMenu(new SimpleMenuProvider((id, inventory, p) -> ChestMenu.threeRows(id, inventory), Component.literal("A chest")));
			SguiUtils.getCurrentGui(players.get(2)).close();
			for (SkinFixtures.Listening player : players) {
				assertThat(tokens(player).size() == 1 && tokens(player).getFirst().getCount() == 1, "closing mid-spin gave " + tokens(player));
				assertThat(player.heard.stream().filter(l -> l.startsWith("You rolled")).count() == 1, "the player was told " + player.heard);
				assertThat(player.sent(ClientboundSoundPacket.class).stream().noneMatch(s -> s.getSound().value().equals(SoundEvents.PLAYER_LEVELUP)), "a landing sound");
			}
			players.get(1).closeContainer();
			assertThat(tokens(players.get(1)).getFirst().getCount() == 1, "closing the chest gave another token");
		} finally {
			for (SkinFixtures.Listening player : players) leave(helper, player);
		}
		helper.succeed();
	}

	/** Leaving the server mid-spin: the token is in the player's saved data, so it's there when they come back. */
	@GameTest
	public void aDisconnectMidSpinSavesTheToken(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().skin("a", "sword").box("a.json5", "{ name: \"A\", skins: [ { skin: \"a\", weight: 1 } ] }");
		fx.reload();
		SkinFixtures.Listening player = holding(helper, "a", 1);
		RollMenu.open(player, fx.skins, "a");
		SkinMenuTests.click(player, BOTTOM);
		helper.getLevel().getServer().getPlayerList().remove(player);
		Path saved = helper.getLevel().getServer().getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(player.getUUID() + ".dat");
		String data;
		try {
			data = NbtIo.readCompressed(saved, NbtAccounter.unlimitedHeap()).toString();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		assertThat(data.contains("metacraft-skins:skin_token") && data.contains("\"metacraft-skins:token\":\"a\""), "the saved player: " + data);
		helper.succeed();
	}

	/** Dying mid-spin, with keepInventory off and on: the token drops at the death spot, never into the inventory. */
	@GameTest
	public void aDeathMidSpinDropsTheTokenAtTheDeathSpot(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().skin("a", "sword").box("a.json5", "{ name: \"A\", skins: [ { skin: \"a\", weight: 1 } ] }");
		fx.reload();
		var rules = helper.getLevel().getServer().getGameRules();
		boolean kept = rules.get(GameRules.KEEP_INVENTORY);
		try {
			for (boolean keepInventory : List.of(false, true)) {
				rules.set(GameRules.KEEP_INVENTORY, keepInventory, helper.getLevel().getServer());
				SkinFixtures.Listening player = holding(helper, "a", 1);
				try {
					RollMenu.open(player, fx.skins, "a");
					SkinMenuTests.click(player, BOTTOM);
					Vec3 spot = player.position();
					player.kill(helper.getLevel());
					List<? extends ItemEntity> dropped = helper.getLevel().getEntities(EntityTypes.ITEM,
							e -> SkinToken.skinOf(e.getItem()).isPresent() && e.position().distanceTo(spot) < 3);
					assertThat(dropped.size() == 1 && SkinToken.skinOf(dropped.getFirst().getItem()).equals(Optional.of("a")), "keepInventory " + keepInventory + ": dropped " + dropped);
					assertThat(tokens(player).isEmpty(), "keepInventory " + keepInventory + ": the token is in the inventory");
					dropped.forEach(ItemEntity::discard);
				} finally {
					leave(helper, player);
				}
			}
		} finally {
			rules.set(GameRules.KEEP_INVENTORY, kept, helper.getLevel().getServer());
		}
		helper.getLevel().getEntities(EntityTypes.ITEM, e -> SkinBox.boxOf(e.getItem()).isPresent()).forEach(ItemEntity::discard);
		helper.succeed();
	}

	/** The result is chosen at the press: a reload that makes it Dormant mid-spin still gives its token. */
	@GameTest
	public void aReloadMidSpinNeverChangesTheResult(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().skin("a", "sword").box("a.json5", "{ name: \"A\", skins: [ { skin: \"a\", weight: 1 } ] }");
		fx.reload();
		SkinFixtures.Listening player = holding(helper, "a", 1);
		try {
			RollMenu.open(player, fx.skins, "a");
			SkinMenuTests.click(player, BOTTOM);
			fx.file("a", "skin.json5", "{ name: \"A\", kind: \"nope\" }");
			fx.reload();
			player.closeContainer();
			assertThat(tokens(player).size() == 1 && SkinToken.skinOf(tokens(player).getFirst()).equals(Optional.of("a")), "the player got " + tokens(player));
		} finally {
			leave(helper, player);
		}
		helper.succeed();
	}

	/** Roll is refused, keeping the box, if the box left the hand (the menu closes), went Dormant or has nothing left to roll (it stays open). */
	@GameTest
	public void rollIsRefusedWhenTheBoxCantRoll(GameTestHelper helper) {
		SkinFixtures fx = alphaBeta();
		SkinFixtures.Listening gone = holding(helper, "ab", 1);
		SkinFixtures.Listening dormant = holding(helper, "ab", 1);
		SkinFixtures.Listening empty = holding(helper, "ab", 1);
		try {
			for (SkinFixtures.Listening player : List.of(gone, dormant, empty)) RollMenu.open(player, fx.skins, "ab");
			gone.getInventory().setItem(0, ItemStack.EMPTY);
			SkinMenuTests.click(gone, BOTTOM);
			assertThat(gone.actionBar.equals(List.of("Your Skin Box is gone.")) && gone.containerMenu == gone.inventoryMenu && tokens(gone).isEmpty(),
					"the box gone: " + gone.actionBar + ", " + SkinMenuTests.title(gone));

			fx.file("a", "skin.json5", "{ name: \"A\", kind: \"nope\" }").file("b", "skin.json5", "{ name: \"B\", kind: \"nope\" }");
			fx.reload();
			SkinMenuTests.click(empty, BOTTOM);
			assertThat(empty.actionBar.equals(List.of("This box has nothing to roll. Your box is kept.")) && SkinMenuTests.title(empty).equals("AB")
					&& empty.getMainHandItem().getCount() == 1, "emptied by a reload: " + empty.actionBar);

			fx.box("ab.json5", "{ name: \"AB\" }");
			fx.reload();
			SkinMenuTests.click(dormant, BOTTOM);
			assertThat(dormant.actionBar.equals(List.of("This Skin Box can't be opened right now.")) && SkinMenuTests.title(dormant).equals("AB")
					&& dormant.getMainHandItem().getCount() == 1, "Dormant after a reload: " + dormant.actionBar);
		} finally {
			for (SkinFixtures.Listening player : List.of(gone, dormant, empty)) leave(helper, player);
		}
		helper.succeed();
	}

	private static final int TOP = 4, REEL = 9, CENTRE = 13, BOTTOM = 22;

	/** A reel skin as the preview (and every unlanded reel slot) shows it: Alpha or Beta, wearing its Look, with its chance. */
	private static void assertPreviewSkin(ServerPlayer player, int slot) {
		String name = name(player, slot);
		assertThat(name.equals("Alpha") || name.equals("Beta"), "reel slot " + slot + " is " + name);
		String id = name.equals("Alpha") ? "a" : "b";
		assertThat(Identifier.fromNamespaceAndPath("skins", id).equals(slot(player, slot).get(DataComponents.ITEM_MODEL)), "reel slot " + slot + " is drawn as "
				+ slot(player, slot).get(DataComponents.ITEM_MODEL));
		assertThat(lore(player, slot).equals(List.of(name.equals("Alpha") ? "75% chance" : "25% chance")), "reel slot " + slot + ": " + lore(player, slot));
	}

	/** A player who joined and arrived, in survival, with {@code count} boxes {@code boxId} in the first hotbar slots, the first in the main hand. */
	private static SkinFixtures.Listening holding(GameTestHelper helper, String boxId, int count) {
		SkinFixtures.Listening player = SkinFixtures.join(helper);
		player.getInventory().clearContent();
		player.setGameMode(GameType.SURVIVAL);
		player.arrive(helper.absoluteVec(new Vec3(2.5, 1, 2.5)));
		for (int slot = 0; slot < count; slot++) player.getInventory().setItem(slot, SkinBox.of(boxId));
		player.heard.clear();
		player.actionBar.clear();
		return player;
	}

	/** The server ticks a player's open menu from {@code ServerPlayer.tick}; a test player's connection isn't ticked, so the test does it. */
	private static void ticking(GameTestHelper helper, ServerPlayer player) {
		helper.onEachTick(() -> player.containerMenu.broadcastChanges());
	}

	private static void leave(GameTestHelper helper, ServerPlayer player) {
		if (!player.hasDisconnected()) helper.getLevel().getServer().getPlayerList().remove(player);
	}

	/** How many Skin Boxes the player carries. */
	private static int boxes(ServerPlayer player) {
		int boxes = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			if (SkinBox.boxOf(player.getInventory().getItem(i)).isPresent()) boxes += player.getInventory().getItem(i).getCount();
		}
		return boxes;
	}

	private static List<ItemStack> tokens(ServerPlayer player) {
		List<ItemStack> tokens = new ArrayList<>();
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			if (SkinToken.skinOf(player.getInventory().getItem(i)).isPresent()) tokens.add(player.getInventory().getItem(i));
		}
		return tokens;
	}

	private static ItemStack slot(ServerPlayer player, int slot) {
		return player.containerMenu.getSlot(slot).getItem();
	}

	private static String name(ServerPlayer player, int slot) {
		return slot(player, slot).getHoverName().getString();
	}

	private static List<String> lore(ServerPlayer player, int slot) {
		return slot(player, slot).getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().stream().map(Component::getString).toList();
	}

	private static void assertRefused(SkinFixtures fx, ServerPlayer player, String boxId, BoxResult expected) {
		ItemStack hand = player.getMainHandItem().copy();
		Skins.Rolled rolled = fx.skins.roll(player, boxId);
		assertThat(rolled.result() == expected && rolled.skin().isEmpty(), "rolling " + boxId + ": " + rolled + ", not " + expected);
		assertThat(ItemStack.matches(hand, player.getMainHandItem()), "a refused roll changed the hand to " + player.getMainHandItem());
	}
}
