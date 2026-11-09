package se.metacraft.skins.ui;

import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.SguiUtils;
import eu.pb4.sgui.api.elements.GuiElement;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import se.metacraft.skins.BoxResult;
import se.metacraft.skins.BoxSummary;
import se.metacraft.skins.Hand;
import se.metacraft.skins.MetacraftSkins;
import se.metacraft.skins.SkinSummary;
import se.metacraft.skins.Skins;
import se.metacraft.skins.item.SkinBox;
import se.metacraft.skins.item.SkinToken;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The Roll menu, opened by right-clicking a Skin Box ({@link RightClick}): a 3-row chest titled with
 * the box's name, the player's own inventory locked. The middle row is the {@link Reel}: before the
 * press it previews nine skins drawn by weight, each with its chance; Roll (a vault under the centre)
 * uses up the box ({@link Skins#roll}), and the reel spins for five seconds, slowing down, with a
 * rising click per step, and lands on the result in the centre with a chime. The menu then stays open
 * on the landed reel until the player closes it; there's no rolling again. Leaving before the press is
 * free. A box with nothing to roll shows a barrier instead of Roll, and keeps the box.
 * <p>
 * The token is given once ({@code settle}): on landing, or at once however the menu closes earlier
 * (Escape, another menu, a dimension change, death, and disconnect or server stop through
 * {@link #left}, before the player is saved). A player who died gets it dropped at the death spot.
 * The menu holds the box's id, never a copy of the box: Roll re-reads it.
 */
public final class RollMenu {
	private RollMenu() {}

	static final int TOP = 4, REEL = 9, BOTTOM = 22;
	private static final int[] SCHEDULE = Reel.schedule();

	/** Opens the Roll menu of box {@code boxId}; a Dormant or unknown box is refused instead. */
	public static void open(ServerPlayer player, Skins skins, String boxId) {
		Optional<BoxSummary> box = skins.box(boxId).filter(b -> !b.dormant());
		if (box.isEmpty()) {
			Feedback.failed(player, Feedback.whyNot(BoxResult.DORMANT_BOX));
			return;
		}
		new Gui(player, skins, box.get()).open();
		Feedback.pageTurn(player);
	}

	/**
	 * The player is leaving the server (Fabric's {@code ServerPlayerEvents.LEAVE}, before they're
	 * saved): a Roll still spinning settles now, so its token is saved with them. The menu's own
	 * close comes after the save, too late.
	 */
	public static void left(ServerPlayer player) {
		if (SguiUtils.getCurrentGui(player) instanceof Gui gui) gui.settle();
	}

	private enum State { READY, ROLLING, LANDED }

	private static final class Gui extends RightClick.Menu {
		private final Skins skins;
		private final String boxId;
		private State state = State.READY;
		/** True until Roll is pressed, and again once the token is given. */
		private boolean settled = true;
		/** The nine skins the reel shows before the press, and how they're drawn. */
		private final List<String> preview = new ArrayList<>();
		private final List<GuiElement> previewShown = new ArrayList<>();
		private List<GuiElement> tape = List.of();
		private String result = "";
		private String resultChance = "";
		private int pressTick, step;

		Gui(ServerPlayer player, Skins skins, BoxSummary box) {
			super(player);
			this.skins = skins;
			this.boxId = box.id();
			setTitle(box.styledName());
			for (int slot = 0; slot < getSize(); slot++) setSlot(slot, Panes.filler(Items.STAINED_GLASS_PANE.gray()));
			setSlot(TOP, marker(Items.STAINED_GLASS_PANE.orange(), Component.literal("▼").withStyle(ChatFormatting.GOLD)));
			if (box.entries().isEmpty()) {
				setSlot(BOTTOM, new GuiElementBuilder(Items.BARRIER).setName(Component.literal("Nothing to roll").withStyle(ChatFormatting.RED))
						.addLoreLine(Component.literal("Every skin in this box is unavailable. Your box is kept.").withStyle(ChatFormatting.GRAY))
						// A double-click's second click (sgui calls back for it too) says nothing more.
						.setCallback((ClickType type) -> {
							if (type != ClickType.MOUSE_DOUBLE_CLICK) Feedback.failed(player, Feedback.whyNot(BoxResult.NOTHING_TO_ROLL));
						}).build());
				return;
			}
			Map<String, String> chances = chances(box.entries());
			for (int i = 0; i < Reel.WIDTH; i++) {
				String skin = BoxSummary.draw(box.entries(), player.getRandom());
				preview.add(skin);
				previewShown.add(reelSkin(skin, chances.get(skin)));
				setSlot(REEL + i, previewShown.getLast());
			}
			setSlot(BOTTOM, GuiElementBuilder.from(SkinBox.drawnAsBox(new ItemStack(Items.VAULT))).setName(Component.literal("Roll").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
					.addLoreLine(Component.literal("Uses up the box").withStyle(ChatFormatting.GRAY)).setCallback(this::roll).build());
		}

		/** Pressing Roll. sgui calls back for every click kind, a double-click's second click too: only the first, from Ready, counts. */
		private void roll() {
			if (state != State.READY) return;
			ServerPlayer player = getPlayer();
			Skins.Rolled rolled = skins.roll(player, boxId);
			if (rolled.result() != BoxResult.DONE) {
				Feedback.failed(player, Feedback.whyNot(rolled.result()));
				if (rolled.result() == BoxResult.BOX_GONE) close();
				return;
			}
			result = rolled.skin().orElseThrow();
			Map<String, String> chances = chances(rolled.entries());
			resultChance = chances.get(result);
			List<String> skinsOnTape = Reel.tape(result, preview, rolled.entries(), player.getRandom());
			List<GuiElement> shown = new ArrayList<>();
			for (int i = 0; i < Reel.STEPS; i++) shown.add(reelSkin(skinsOnTape.get(i), chances.get(skinsOnTape.get(i))));
			shown.addAll(previewShown);
			tape = shown;
			state = State.ROLLING;
			settled = false;
			pressTick = now();
			step = 0;
			setSlot(BOTTOM, marker(Items.STAINED_GLASS_PANE.orange(), Component.literal("▲").withStyle(ChatFormatting.GOLD)));
			click();
		}

		/** Every server tick (and after every click): catches up every step that's due by now, so clicking never speeds the reel up. */
		@Override
		public void onTick() {
			int elapsed = now() - pressTick;
			while (state == State.ROLLING && step < Reel.STEPS && SCHEDULE[step] <= elapsed) {
				step++;
				for (int i = 0; i < Reel.WIDTH; i++) setSlot(REEL + i, tape.get(i - step + Reel.STEPS));
				if (step == Reel.STEPS) land();
				else click();
			}
		}

		private void land() {
			state = State.LANDED;
			Component name = skins.styledName(result);
			setSlot(REEL + Reel.CENTRE, landed());
			setSlot(TOP, marker(Items.STAINED_GLASS_PANE.lime(), Component.literal("▼ ").withStyle(ChatFormatting.GREEN).append(name)));
			setSlot(BOTTOM, marker(Items.STAINED_GLASS_PANE.lime(), Component.literal("▲ ").withStyle(ChatFormatting.GREEN).append(name)));
			Feedback.sound(getPlayer(), BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.PLAYER_LEVELUP), 1f, 1f);
			settle();
		}

		/** The click of a step: a note block hat, higher with each step taken. */
		private void click() {
			Feedback.sound(getPlayer(), SoundEvents.NOTE_BLOCK_HAT, 0.6f, Reel.pitch(step));
		}

		/**
		 * Gives the token, once, and only after a press: into the inventory (what doesn't fit at the
		 * player's feet, for them alone), or for a player who died, dropped at the death spot, whatever
		 * {@code keepInventory} says (their inventory has been dropped or kept by then). A spin still
		 * going stops where it is.
		 */
		private void settle() {
			if (settled) return;
			settled = true;
			state = State.LANDED;
			ServerPlayer player = getPlayer();
			ItemStack token = SkinToken.of(result, 1);
			if (player.isDeadOrDying()) {
				ItemEntity drop = player.createItemStackToDrop(token, true, false);
				if (drop != null) drop.level().addFreshEntity(drop);
			} else {
				Hand.give(player, token);
			}
			Feedback.rolled(player, skins.styledName(result));
		}

		/** Escape, another menu opening over it, a dimension change, a code close(), and the late close after death or disconnect. */
		@Override
		public void onRemoved() {
			settle();
		}

		/** A code close(), and death (sgui reports it after the death loot drops). */
		@Override
		public void onManualClose() {
			super.onManualClose();
			settle();
		}

		@Override
		public void handleException(Throwable throwable) {
			MetacraftSkins.LOGGER.error("[{}] the Roll menu of {} for {} failed", MetacraftSkins.MOD_ID, boxId, getPlayer().getGameProfile().name(), throwable);
		}

		private int now() {
			return getPlayer().level().getServer().getTickCount();
		}

		/** A skin on the reel: its Look as in the Skin Menu, named in its colours, with its chance. */
		private GuiElement reelSkin(String skinId, String chance) {
			return look(skinId).addLoreLine(Component.literal(chance + " chance").withStyle(ChatFormatting.GRAY)).build();
		}

		/** The landed result, glinting: its chance, then what it fits, its release, its Perks and its description, as the Skin Menu shows them. */
		private GuiElement landed() {
			GuiElementBuilder icon = look(result).addLoreLine(Component.literal(resultChance + " chance").withStyle(ChatFormatting.GRAY));
			shown(result).ifPresent(s -> SkinMenu.lookLines(s, s.fit().get()).forEach(icon::addLoreLine));
			return icon.glow(true).build();
		}

		/** A skin's Look, or (one gone Dormant or unknown since the box was read) a paper named by its id. */
		private GuiElementBuilder look(String skinId) {
			return shown(skinId).map(s -> SkinMenu.lookOnly(s, s.fit().get()))
					.orElseGet(() -> new GuiElementBuilder(Items.PAPER).setName(Component.literal(skinId).withStyle(ChatFormatting.GRAY)));
		}

		/** The skin, if it can be shown with its Look: loaded, with a Kind. */
		private Optional<SkinSummary> shown(String skinId) {
			return skins.skin(skinId).filter(s -> !s.dormant() && s.fit().isPresent());
		}
	}

	/** Each usable skin's chance as players read it. */
	private static Map<String, String> chances(List<BoxSummary.Entry> entries) {
		return entries.stream().collect(Collectors.toMap(BoxSummary.Entry::skin, BoxSummary.Entry::chance));
	}

	private static GuiElement marker(Item pane, Component name) {
		return new GuiElementBuilder(pane).setName(name).build();
	}

}
