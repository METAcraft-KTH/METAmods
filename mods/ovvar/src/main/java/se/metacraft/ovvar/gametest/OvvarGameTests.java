package se.metacraft.ovvar.gametest;

import se.metacraft.ovvar.OvvarConfig;
import se.metacraft.ovvar.content.Chapter;
import se.metacraft.ovvar.content.Looks;
import se.metacraft.ovvar.content.ModContent;
import se.metacraft.ovvar.content.item.OvveItem;
import se.metacraft.ovvar.content.item.OvveTopItem;
import se.metacraft.ovvar.content.Patches;
import se.metacraft.ovvar.content.Placement;
import se.metacraft.ovvar.content.Spot;
import se.metacraft.ovvar.content.SpotPlacements;
import se.metacraft.ovvar.sewing.Seam;
import se.metacraft.ovvar.sewing.SewingFont;
import se.metacraft.ovvar.sewing.SewingGame;
import se.metacraft.ovvar.sewing.StandAim;
import se.metacraft.ovvar.sewing.StandDisplays;
import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.Rotations;
import net.minecraft.gametest.framework.GameTestHelper;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.GameType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * {@code ./gradlew runGametest}. The aiming tests hold the sewing aim to the forward mapping:
 * for every cell, on stands in several poses and facings, a ray straight at the cell's centre
 * must resolve to that cell — near faces from outside, far faces (sneaking) from the other
 * side of the part. The stitching test plays the minigame's clicks the way the dialog sends them.
 */
public final class OvvarGameTests {
	private static final Rotations REST = new Rotations(0, 0, 0);
	private static final Rotations ARMS_OUT_R = new Rotations(0, 0, 60), ARMS_OUT_L = new Rotations(0, 0, -60);
	private static final Rotations LEGS_APART_R = new Rotations(0, 0, 25), LEGS_APART_L = new Rotations(0, 0, -25);

	@GameTest
	public void aimEveryCellAtRest(GameTestHelper helper) {
		aimEveryCell(helper, 0, REST, REST, REST, REST);
	}

	@GameTest
	public void aimEveryCellPosedAndTurned(GameTestHelper helper) {
		aimEveryCell(helper, 137, ARMS_OUT_R, ARMS_OUT_L, LEGS_APART_R, LEGS_APART_L);
	}

	@GameTest
	public void aimFarFacesWhenSneaking(GameTestHelper helper) {
		ArmorStand stand = stand(helper, 210, REST, REST, REST, REST);
		List<String> wrong = new ArrayList<>();
		for (Spot spot : Spot.values()) {
			if (spot == Spot.SEAT) continue;
			// Look at the cell from just inside its part: the ray exits through this face, which
			// sneaking picks (the far face of the part it is in).
			StandAim.CellPoint cell = StandAim.cell(stand, spot);
			Vec3 eye = cell.centre().subtract(cell.normal().scale(0.15));
			StandAim.Hit hit = StandAim.aim(eye, cell.normal(), stand, true, 6);
			if (hit == null || hit.spot() != spot) wrong.add(spot.id() + " → " + (hit == null ? "miss" : hit.spot()));
		}
		if (!wrong.isEmpty()) helper.fail("sneak-aim off for " + wrong.size() + " cell(s): " + wrong);
		helper.succeed();
	}

	/**
	 * Right-clicking a stand's <b>chest</b> with an empty hand takes the whole ovve off, into the
	 * hand, and leaves no companion top behind.
	 *
	 * <p>It used to be vanilla swapping the chest slot, which holds the companion top
	 * ({@link OvveTopItem}) while the ovve's top is up — and that is not a possession: out of a chest
	 * slot it deletes itself on the next tick, while the ovve's own tick puts a fresh one straight
	 * back, so the click looked like the top jumping back and nothing else happening. The garment is
	 * one item in the legs slot, so taking its top off the stand means taking the ovve off.
	 *
	 * <p>A click on a leg is unchanged: our handler passes it, and vanilla's own swap — which is what
	 * the second half of this test calls — still hands the ovve over.
	 */
	@GameTest(maxTicks = 200)
	public void clickingAStandsChestTakesTheWholeOvveOff(GameTestHelper helper) {
		ArmorStand stand = stand(helper, 0, REST, REST, REST, REST);
		ItemStack ovve = new ItemStack(ModContent.ovve(Chapter.values()[0]));
		Placement placement = new Placement(Spot.SLEEVE_OUT_TOP_R, Patches.get("itk"));
		Looks.setSewn(ovve, SpotPlacements.fromList(List.of(placement)).getOrThrow());
		OvveItem.setTopUp(ovve, true);
		stand.setItemSlot(EquipmentSlot.LEGS, ovve);
		ServerPlayer player = sewer(helper, stand);
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		helper.runAfterDelay(5, () -> {
			// The companion top is there to be clicked on (the ovve's tick put it in the chest slot).
			if (!(stand.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof OvveTopItem)) {
				helper.fail("the stand has no companion top in its chest slot, so this test cannot click on one");
			}
			// Aimed at the stand's body, which is the chest's own part.
			StandAim.CellPoint at = StandAim.cell(stand, Spot.FRONT_TOP_LEFT);
			aimAt(player, at);
			InteractionResult result = UseEntityCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.MAIN_HAND, stand, null);
			if (!result.consumesAction()) helper.fail("a click on the stand's chest was not taken: " + result);
			if (!stand.getItemBySlot(EquipmentSlot.LEGS).isEmpty()) helper.fail("the ovve is still on the stand's legs");
			if (!stand.getItemBySlot(EquipmentSlot.CHEST).isEmpty()) {
				helper.fail("a companion top was left on the stand: " + stand.getItemBySlot(EquipmentSlot.CHEST));
			}
			ItemStack held = player.getMainHandItem();
			if (!(held.getItem() instanceof OvveItem)) helper.fail("the player is holding " + held + ", not the ovve");
			else if (!placement.equals(Looks.at(held, Spot.SLEEVE_OUT_TOP_R))) helper.fail("the ovve they got has lost its patch: " + Looks.sewn(held));

			// And a leg: our handler leaves it to vanilla, whose swap hands the ovve over as before.
			ArmorStand other = stand(helper, 0, REST, REST, REST, REST);
			ItemStack second = new ItemStack(ModContent.ovve(Chapter.values()[0]));
			OvveItem.setTopUp(second, true);
			other.setItemSlot(EquipmentSlot.LEGS, second);
			ServerPlayer legsPlayer = sewer(helper, other);
			legsPlayer.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			aimAt(legsPlayer, StandAim.cell(other, Spot.LEG_FRONT_TOP_R));
			StandAim.Hit hit = StandAim.aim(legsPlayer.getEyePosition(), legsPlayer.getViewVector(1.0f), other, false, 6);
			if (hit == null || !hit.part().endsWith("leg")) helper.fail("the second player is not aimed at a leg: " + (hit == null ? "miss" : hit.part()));
			InteractionResult legs = UseEntityCallback.EVENT.invoker().interact(legsPlayer, legsPlayer.level(), InteractionHand.MAIN_HAND, other, null);
			if (legs != InteractionResult.PASS) helper.fail("a click on a leg was taken by us (" + legs + "), not left to vanilla's swap");
			other.interact(legsPlayer, InteractionHand.MAIN_HAND, new Vec3(0, 0.5, 0));   // vanilla: y 0.5 is the legs slot
			if (!other.getItemBySlot(EquipmentSlot.LEGS).isEmpty()) helper.fail("vanilla's legs swap left the ovve on the stand");
			if (!(legsPlayer.getMainHandItem().getItem() instanceof OvveItem)) {
				helper.fail("the legs click did not hand the ovve over: " + legsPlayer.getMainHandItem());
			}
			helper.succeed();
		});
	}

	/** Puts a player where they are looking straight at a cell, from just outside it. */
	private static void aimAt(ServerPlayer player, StandAim.CellPoint at) {
		Vec3 from = at.centre().add(at.normal().scale(1.5));
		player.setPos(from.x, from.y - player.getEyeHeight(), from.z);
		Vec3 d = at.centre().subtract(player.getEyePosition());
		double flat = Math.sqrt(d.x * d.x + d.z * d.z);
		player.setYRot((float) Math.toDegrees(Math.atan2(-d.x, d.z)));
		player.setXRot((float) -Math.toDegrees(Math.atan2(d.y, flat)));
		player.setYHeadRot(player.getYRot());
	}

	@GameTest
	public void stitchingSewsOnTheLastPull(GameTestHelper helper) {
		ArmorStand stand = stand(helper, 0, REST, REST, REST, REST);
		ItemStack ovve = new ItemStack(ModContent.ovve(Chapter.values()[0]));
		stand.setItemSlot(EquipmentSlot.LEGS, ovve);
		ServerPlayer player = sewer(helper, stand);
		Patches.Patch itk = Patches.get("itk");
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModContent.patchItem(itk), 3));
		Placement placement = new Placement(Spot.FRONT_TOP_LEFT, itk);

		SewingGame.start(player, stand, placement, itk);
		CompoundTag stale = SewingGame.nextPull(player);
		if (stale == null) helper.fail("no seam open after start");
		// The dialog must encode the way the packet sends it (a bad button or body would fail here).
		JsonElement json = Dialog.DIRECT_CODEC.encodeStart(JsonOps.INSTANCE, SewingGame.dialog(player))
				.getOrThrow(message -> new IllegalStateException("dialog does not encode: " + message));
		if (!json.toString().contains("ovvar:pull")) helper.fail("no pull button in " + json);
		// A wrong stitch number (an old dialog, a double click) must not count.
		CompoundTag wrong = stale.copy();
		wrong.putInt("stitch", 5);
		SewingGame.click(player, SewingGame.PULL, Optional.of(wrong));
		if (SewingGame.nextPull(player).getIntOr("stitch", -1) != 0) helper.fail("a stale pull advanced the seam");
		// Pull until sewn; every pull but the last leaves the ovve untouched.
		int pulls = 0;
		while (Looks.at(stand.getItemBySlot(EquipmentSlot.LEGS), Spot.FRONT_TOP_LEFT) == null) {
			CompoundTag next = SewingGame.nextPull(player);
			if (next == null) helper.fail("seam closed after " + pulls + " pull(s) without sewing");
			if (next.getIntOr("stitch", -1) != pulls) helper.fail("expected stitch " + pulls + ", dialog offers " + next);
			SewingGame.click(player, SewingGame.PULL, Optional.of(next));
			if (++pulls > 32) helper.fail("still not sewn after " + pulls + " pulls");
		}
		if (!placement.equals(Looks.at(stand.getItemBySlot(EquipmentSlot.LEGS), Spot.FRONT_TOP_LEFT))) helper.fail("sewn placement is wrong");
		if (SewingGame.nextPull(player) != null) helper.fail("seam still open after sewing");
		int left = player.getMainHandItem().getCount();
		if (!player.isCreative() && left != 2) helper.fail("expected one patch used, " + left + " left of 3");
		helper.succeed();
	}

	/**
	 * Every sprite label in every state of every seam measures exactly what the client centres
	 * without scrolling (the button's width minus its insets) and draws only within 1 px of its
	 * button: the rules {@link SewingFont} rests on. Also that each dialog encodes.
	 */
	@GameTest
	public void sewingLabelsFitTheirButtons(GameTestHelper helper) {
		int labels = 0;
		for (Chapter chapter : Chapter.values()) {
			for (Patches.Patch patch : Patches.all()) {
				Spot spot = patch.seat() ? Spot.SEAT : Spot.FRONT_TOP_LEFT;
				for (int stitches = OvvarConfig.MIN_STITCHES; stitches <= OvvarConfig.MAX_STITCHES; stitches++) {
					for (int done = 0; done < stitches; done++) {
						Dialog dialog = SewingGame.dialog(chapter, patch, spot, stitches, done);
						if (chapter == Chapter.values()[0] && done == 0) {
							Dialog.DIRECT_CODEC.encodeStart(JsonOps.INSTANCE, dialog)
									.getOrThrow(message -> new IllegalStateException("dialog does not encode: " + message));
						}
						MultiActionDialog multi = (MultiActionDialog) dialog;
						List<ActionButton> buttons = new ArrayList<>(multi.actions());
						multi.exitAction().ifPresent(buttons::add);
						int pulls = 0;
						for (ActionButton button : buttons) {
							String where = chapter.id + "/" + patch.id() + " " + done + "/" + stitches;
							int width = button.button().width();
							String text = button.button().label().getString();
							int measured = SewingFont.width(text);
							if (measured != width - 2 * SewingFont.LABEL_INSET) {
								helper.fail(where + ": a " + width + " px button's label measures " + measured);
							}
							// A cell's glyph covers its button and 1 px around; only the last cell reaches back over the picture.
							int[] extent = SewingFont.extent(text);
							if (extent[0] < SewingFont.overlayX(0) || extent[1] > width + 2 * SewingFont.OVERHANG) {
								helper.fail(where + ": a label draws from " + extent[0] + " to " + extent[1] + " on a " + width + " px button");
							}
							if (button.action().isPresent() && buttons.indexOf(button) < multi.actions().size()) pulls++;
							labels++;
						}
						if (pulls != 1) helper.fail(chapter.id + "/" + patch.id() + " " + done + "/" + stitches + ": " + pulls + " needle buttons");
					}
				}
			}
		}
		if (labels == 0) helper.fail("no labels checked");
		helper.succeed();
	}

	@GameTest
	public void cuttingTheThreadSewsNothing(GameTestHelper helper) {
		ArmorStand stand = stand(helper, 0, REST, REST, REST, REST);
		stand.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModContent.ovve(Chapter.values()[0])));
		ServerPlayer player = sewer(helper, stand);
		Patches.Patch itk = Patches.get("itk");
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModContent.patchItem(itk), 3));
		SewingGame.start(player, stand, new Placement(Spot.BACK_TOP_RIGHT, itk), itk);
		SewingGame.click(player, SewingGame.PULL, Optional.of(SewingGame.nextPull(player)));
		SewingGame.click(player, SewingGame.CUT, Optional.empty());
		if (SewingGame.nextPull(player) != null) helper.fail("seam still open after cutting");
		if (!Looks.sewn(stand.getItemBySlot(EquipmentSlot.LEGS)).isEmpty()) helper.fail("something was sewn");
		if (player.getMainHandItem().getCount() != 3) helper.fail("a patch was used up");
		helper.succeed();
	}

	/**
	 * {@link OvveItem#patchLines} is what keeps a dozen patches from turning the tooltip into a
	 * wall of text: a repeat becomes "name ×N" rather than a line of its own, wrapping never cuts a
	 * name in half however tight the width, and what still does not fit shrinks the last line to
	 * make room for "… +N more" — N counting patches left out, not names, and the line with the
	 * suffix on it never runs past the width either. The patches here are throwaway ones built
	 * straight from the record, not the catalogue: their names are picked so the wrapping math
	 * comes out to an exact, hand-checked answer.
	 */
	@GameTest
	public void patchLinesGroupsWrapsAndCapsOverflow(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();

		// A repeat of the same patch collapses into one "name ×N" token, not a line per placement.
		Patches.Patch p = new Patches.Patch("p", "Patch");
		List<Placement> pair = List.of(new Placement(Spot.FRONT_TOP_LEFT, p), new Placement(Spot.FRONT_TOP_RIGHT, p));
		List<String> counted = OvveItem.patchLines(pair, 40, 3);
		if (!counted.equals(List.of("Patch ×2"))) wrong.add("duplicate count: " + counted);

		// However tight the width, a name is never split — the first token on a line always goes on
		// whole, even past the width, rather than being cut.
		Patches.Patch long_ = new Patches.Patch("long", "Supercalifragilisticexpialidocious");
		List<String> tooTight = OvveItem.patchLines(List.of(new Placement(Spot.FRONT_TOP_LEFT, long_)), 10, 1);
		if (!tooTight.equals(List.of("Supercalifragilisticexpialidocious"))) wrong.add("no-split at a tight width: " + tooTight);

		// Four names, counts 2/3/1/1 (7 patches total, 4 names): at width 16 the first two names
		// (5 + 5 chars) plus the "… +N more" suffix fill the one allowed line exactly, and N is 5 —
		// the patches in the two names left out (BB ×3 and the two singles), not 2, which is what it
		// would be if the suffix counted names instead.
		Patches.Patch aa = new Patches.Patch("aa", "AA"), bb = new Patches.Patch("bb", "BB");
		Patches.Patch cc = new Patches.Patch("cc", "CC"), dd = new Patches.Patch("dd", "DD");
		List<Placement> seven = List.of(
				new Placement(Spot.FRONT_TOP_LEFT, aa), new Placement(Spot.FRONT_TOP_RIGHT, aa),
				new Placement(Spot.FRONT_LOW_LEFT, bb), new Placement(Spot.FRONT_LOW_RIGHT, bb), new Placement(Spot.BACK_TOP_LEFT, bb),
				new Placement(Spot.BACK_TOP_RIGHT, cc),
				new Placement(Spot.SLEEVE_OUT_TOP_R, dd));
		List<String> overflow = OvveItem.patchLines(seven, 16, 1);
		if (!overflow.equals(List.of("AA ×2, … +5 more"))) wrong.add("overflow line: " + overflow);
		if (overflow.get(0).length() > 16) wrong.add("overflow line runs past its width: '" + overflow.get(0) + "'");

		// The same seven placements with no room for even one name alongside the suffix: a bare
		// "… +N more" line, N counting every one of the 7 patches (3 names), still inside its width.
		List<String> bare = OvveItem.patchLines(seven, 10, 1);
		if (!bare.equals(List.of("… +7 more"))) wrong.add("bare overflow: " + bare);

		if (!wrong.isEmpty()) helper.fail(String.join("; ", wrong));
		helper.succeed();
	}

	/**
	 * {@code /ovvar shaders} sends three lines, the second a link to the configured patcher page
	 * (and the hover shows it); change the config and the link follows. Runs in one tick on the
	 * server thread, so nothing else sees the changed config; it is put back in a finally.
	 */
	@GameTest
	public void shadersCommandLinksThePage(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		net.minecraft.server.MinecraftServer server = helper.getLevel().getServer();
		se.metacraft.ovvar.ShadersConfig before = OvvarConfig.get().shaders();
		try {
			String firstUrl = shadersLink(helper, server, player, before.patcherUrlOrDefault());
			if (firstUrl == null) return;
			String other = "https://example.org/x";
			OvvarConfig.modify(config -> config.shaders(new se.metacraft.ovvar.ShadersConfig(other)));
			shadersLink(helper, server, player, other);
			OvvarConfig.modify(config -> config.shaders(new se.metacraft.ovvar.ShadersConfig("  ")));
			shadersLink(helper, server, player, se.metacraft.ovvar.ShadersConfig.DEFAULT_PATCHER_URL);
		} finally {
			OvvarConfig.modify(config -> config.shaders(before));
		}
		helper.succeed();
	}

	/** Runs the command, checks its three lines and the link on the second; returns the link or null after failing. */
	private static String shadersLink(GameTestHelper helper, net.minecraft.server.MinecraftServer server, ServerPlayer player, String expected) {
		List<Component> lines = new ArrayList<>();
		// The command writes to the player's own connection (the mock's embedded channel): read the chat packets off it.
		io.netty.channel.embedded.EmbeddedChannel channel;
		try {
			var field = net.minecraft.network.Connection.class.getDeclaredField("channel");
			field.setAccessible(true);
			var connection = net.minecraft.server.network.ServerCommonPacketListenerImpl.class.getDeclaredField("connection");
			connection.setAccessible(true);
			channel = (io.netty.channel.embedded.EmbeddedChannel) field.get(connection.get(player.connection));
		} catch (ReflectiveOperationException | ClassCastException | NullPointerException e) {
			helper.fail("can't reach the mock player's channel: " + e);
			return null;
		}
		channel.outboundMessages().clear();
		try {
			int ran = server.getCommands().getDispatcher().execute("ovvar shaders", player.createCommandSourceStack());
			if (ran != 1) { helper.fail("/ovvar shaders returned " + ran); return null; }
		} catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
			helper.fail("/ovvar shaders did not run: " + e.getMessage());
			return null;
		}
		channel.runPendingTasks();
		Object sent;
		while ((sent = channel.readOutbound()) != null) {
			if (sent instanceof net.minecraft.network.protocol.game.ClientboundSystemChatPacket chat) lines.add(chat.content());
		}
		if (lines.size() != 3) { helper.fail("/ovvar shaders sent " + lines.size() + " lines, wanted 3: " + lines); return null; }
		var click = lines.get(1).getStyle().getClickEvent();
		if (!(click instanceof net.minecraft.network.chat.ClickEvent.OpenUrl open) || !open.uri().toString().equals(expected)) {
			helper.fail("second line should open " + expected + ", has " + click);
			return null;
		}
		if (!(lines.get(1).getStyle().getHoverEvent() instanceof net.minecraft.network.chat.HoverEvent.ShowText hover)
				|| !hover.value().getString().equals(expected)) {
			helper.fail("second line's hover should show " + expected);
			return null;
		}
		return expected;
	}

	/**
	 * However many patches an ovve carries, its tooltip stays six lines or fewer under the name: the
	 * status line, at most {@link OvveItem#PATCH_LIST_LINES} lines of patches, one hint line and the
	 * shaders line ({@link OvveItem#SHADERS_HINT}; unpatched shaderpacks draw the limbs as a
	 * checker). This sews every catalogue patch on (several times over, where a cell exists to put a
	 * repeat on — the seat has only the one cell, so its patch gets it once) and reads the tooltip
	 * {@code OvveItem} itself would send a client.
	 */
	@GameTest
	public void aFullyPatchedOvvesTooltipIsStillSixLinesOrLess(GameTestHelper helper) {
		List<Patches.Patch> catalogue = Patches.all();
		List<Placement> placements = new ArrayList<>();
		int next = 0;
		for (Spot spot : Spot.values()) {
			// The seat and the two cells it overlaps (the backs of the legs) can only hold one
			// patch between them; the seat itself takes that below.
			if (spot == Spot.SEAT || Spot.SEAT.overlapping().contains(spot)) continue;
			Patches.Patch patch;
			do {
				patch = catalogue.get(next++ % catalogue.size());
			} while (patch.seat());
			placements.add(new Placement(spot, patch));
		}
		catalogue.stream().filter(Patches.Patch::seat).findFirst()
				.ifPresent(seatPatch -> placements.add(new Placement(Spot.SEAT, seatPatch)));

		ItemStack ovve = new ItemStack(ModContent.ovve(Chapter.values()[0]));
		Looks.setSewn(ovve, SpotPlacements.fromList(placements).getOrThrow());
		List<Component> tooltip = new ArrayList<>();
		((OvveItem) ovve.getItem()).modifyClientTooltip(tooltip, ovve, null);
		int max = 1 + OvveItem.PATCH_LIST_LINES + 1 + 1;   // status + patch list + hint + shaders line, no "no patches" line
		if (tooltip.size() > max) {
			helper.fail("a fully-patched ovve's tooltip is " + tooltip.size() + " lines, wanted at most " + max + ": " + tooltip);
		}
		helper.succeed();
	}

	/**
	 * A player two blocks in front of the stand with a connection (an embedded channel), so it
	 * can be sent dialogs. Only the deprecated helper wires one up; the replacement has none.
	 */
	@SuppressWarnings("removal")
	private static ServerPlayer sewer(GameTestHelper helper, ArmorStand stand) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.setPos(stand.getX(), stand.getY(), stand.getZ() + 2);
		return player;
	}

	private static void aimEveryCell(
		GameTestHelper helper, float yaw, Rotations rightArm, Rotations leftArm, Rotations rightLeg, Rotations leftLeg
	) {
		ArmorStand stand = stand(helper, yaw, rightArm, leftArm, rightLeg, leftLeg);
		List<String> wrong = new ArrayList<>();
		for (Spot spot : Spot.values()) {
			if (spot == Spot.SEAT) continue;
			StandAim.CellPoint cell = StandAim.cell(stand, spot);
			Vec3 eye = cell.centre().add(cell.normal().scale(0.3));   // close in: nothing else in the way
			StandAim.Hit hit = StandAim.aim(eye, cell.normal().scale(-1), stand, false, 6);
			if (hit == null || hit.spot() != spot) wrong.add(spot.id() + " → " + (hit == null ? "miss" : hit.spot()));
		}
		if (!wrong.isEmpty()) helper.fail("aim off for " + wrong.size() + " cell(s): " + wrong);
		helper.succeed();
	}

	/**
	 * The config's stitch count is for a cell-sized patch (a 32-texel outline); a longer outline
	 * gets proportionally more holes, within the dialog's range.
	 */
	@GameTest
	public void stitchesScaleWithTheOutline(GameTestHelper helper) {
		record Case(String patch, int base, int expected) {}
		List<Case> cases = List.of(
				new Case("itk", 6, 9),        // 12×12, 48 texels: 9 exactly
				new Case("nyckeln0x2", 6, 9),  // 12×12, 48 texels: 9 exactly (Nyckeln'26, the 8×8 this once checked, is gone)
				new Case("rivals", 6, 9),     // the 16×8 seat patch, 48 texels: 9 exactly
				new Case("itk", 12, 16),      // 18 capped at the dialog's most
				new Case("nyckeln0x2", 1, 2),  // scales down with the base
				new Case("nyckeln0x2", 0, OvvarConfig.MIN_STITCHES));   // never below the least the dialog allows
		List<String> wrong = new ArrayList<>();
		for (Case c : cases) {
			int got = Seam.stitchesFor(Patches.get(c.patch), c.base);
			if (got != c.expected) wrong.add(c.patch + " at base " + c.base + ": expected " + c.expected + ", got " + got);
		}
		if (!wrong.isEmpty()) helper.fail("stitch counts off: " + wrong);
		// A game started on an oversize patch sews with the scaled count, and its dialog says so.
		ArmorStand stand = stand(helper, 0, REST, REST, REST, REST);
		stand.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModContent.ovve(Chapter.values()[0])));
		ServerPlayer player = sewer(helper, stand);
		Patches.Patch itk = Patches.get("itk");
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModContent.patchItem(itk)));
		SewingGame.start(player, stand, new Placement(Spot.FRONT_TOP_LEFT, itk), itk);
		String json = Dialog.DIRECT_CODEC.encodeStart(JsonOps.INSTANCE, SewingGame.dialog(player))
				.getOrThrow(message -> new IllegalStateException("dialog does not encode: " + message)).toString();
		if (!json.contains("Stitch 1 of " + Seam.stitchesFor(itk, OvvarConfig.get().stitches()))) helper.fail("the ITK dialog does not offer the scaled count: " + json);
		helper.succeed();
	}

	/**
	 * A stand spawned in the air (as {@code /ovvar showcase} does when flying) falls and lands;
	 * once it rests, every patch sprite must lie on its cell — not where the cell was a tick
	 * before the stand stopped.
	 */
	@GameTest(maxTicks = 120)
	public void spritesFollowAStandThatFalls(GameTestHelper helper) {
		ArmorStand stand = helper.spawn(EntityTypes.ARMOR_STAND, new BlockPos(2, 6, 2));
		ItemStack ovve = new ItemStack(ModContent.ovve(Chapter.values()[0]));
		Placement placement = new Placement(Spot.LEG_FRONT_TOP_R, Patches.get("itk"));
		Looks.setSewn(ovve, SpotPlacements.fromList(List.of(placement)).getOrThrow());
		stand.setItemSlot(EquipmentSlot.LEGS, ovve);
		helper.runAfterDelay(60, () -> {
			if (!stand.onGround()) helper.fail("stand still falling after 60 ticks, at y " + stand.getY());
			List<StandDisplays.Sprite> sprites = StandDisplays.sprites(stand);
			if (sprites.isEmpty()) helper.fail("no sprites on the stand");
			for (StandDisplays.Sprite sprite : sprites) {
				Vec3 cell = StandAim.cell(stand, sprite.placement().spot()).centre();
				double off = sprite.pos().distanceTo(cell);
				if (off > 0.03) helper.fail(sprite.placement().key() + " sprite is " + String.format("%.3f", off) + " blocks off its cell after landing");
			}
			helper.succeed();
		});
	}

	/**
	 * {@code /give @s ovvar:data_ovve[!minecraft:equippable]} is a legal item: whatever of ours
	 * lacks the component still reaches a client, as something that simply cannot be worn.
	 */
	@GameTest
	public void anItemWithoutEquippableStillReachesClients(GameTestHelper helper) {
		ServerPlayer viewer = helper.makeMockServerPlayerInLevel();
		List<String> wrong = new ArrayList<>();
		for (Chapter chapter : Chapter.values()) {
			List<ItemStack> stacks = new ArrayList<>(List.of(new ItemStack(ModContent.ovve(chapter))));
			if (ModContent.top(chapter) != null) stacks.add(new ItemStack(ModContent.top(chapter)));
			if (ModContent.feet(chapter) != null) stacks.add(new ItemStack(ModContent.feet(chapter)));
			for (ItemStack stack : stacks) {
				stack.remove(DataComponents.EQUIPPABLE);
				try {
					ItemStack client = PolymerItemUtils.getPolymerItemStack(stack, viewer.connection.getPacketContext(), helper.getLevel().registryAccess());
					Equippable equippable = client.get(DataComponents.EQUIPPABLE);
					if (equippable != null && equippable.swappable()) wrong.add(stack.getItem() + " can be swapped on at the client");
				} catch (RuntimeException e) {
					wrong.add(stack.getItem() + ": " + e);
				}
			}
		}
		if (!wrong.isEmpty()) helper.fail("without an equippable component: " + wrong);
		helper.succeed();
	}

	private static ArmorStand stand(GameTestHelper helper, float yaw, Rotations rightArm, Rotations leftArm, Rotations rightLeg, Rotations leftLeg) {
		ArmorStand stand = helper.spawn(EntityTypes.ARMOR_STAND, new BlockPos(2, 1, 2));
		stand.setYRot(yaw);
		stand.setYBodyRot(yaw);
		stand.yBodyRotO = yaw;
		stand.setRightArmPose(rightArm);
		stand.setLeftArmPose(leftArm);
		stand.setRightLegPose(rightLeg);
		stand.setLeftLegPose(leftLeg);
		stand.setNoGravity(true);
		return stand;
	}
}
