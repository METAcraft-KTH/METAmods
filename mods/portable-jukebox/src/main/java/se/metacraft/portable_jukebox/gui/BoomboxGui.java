package se.metacraft.portable_jukebox.gui;

import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.gui.layered.Layer;
import eu.pb4.sgui.api.gui.layered.LayeredGui;
import it.unimi.dsi.fastutil.objects.ReferenceSortedSets;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.TooltipDisplay;
import se.metacraft.core.util.helper.PlayerInventoryHelper;
import se.metacraft.lib.util.EntityRef;
import se.metacraft.portable_jukebox.Boombox;
import se.metacraft.portable_jukebox.item.BoomboxItem;
import se.metacraft.portable_jukebox.item.components.Autoplay;
import se.metacraft.portable_jukebox.item.components.Components;
import se.metacraft.portable_jukebox.item.components.BoomboxConfiguration;

import java.util.*;
import java.util.function.UnaryOperator;

/**
 * The boombox menu: controls above the disc storage, each shown only if the boombox has it.
 * Every control is a display item handled by click here; the disc slot is the only real slot.
 */
public class BoomboxGui extends LayeredGui {

	private static final int WIDTH = 9;
	private static final int CONTROL_ROWS = 2;
	private static final int MAX_ROWS = 6;
	private static final int DISC_ROW = 0;
	private static final int DISC_COLUMN = 4;
	private static final int PLAY_COLUMN = 4;
	private static final int SKIP_COLUMN = 5;
	private static final int SHUFFLE_COLUMN = 3;
	private static final int PLAYLIST_COLUMN = 5;
	private static final int VOLUME_MINUS_COLUMN = 0;
	private static final int VOLUME_PLUS_COLUMN = 1;
	private static final int VOLUME_DISPLAY_COLUMN = 1;
	private static final int SPEED_DISPLAY_COLUMN = 7;
	private static final int SPEED_MINUS_COLUMN = 7;
	private static final int SPEED_PLUS_COLUMN = 8;

	/** Ticks after the last speed or volume click before a playing song restarts with the new sound. */
	private static final int RESTART_DELAY = 15;

	private static final FontDescription MENU_FONT = new FontDescription.Resource(
			Identifier.fromNamespaceAndPath("portable_jukebox", "boombox_gui"));

	private final BoomboxInventory inventory;
	private final BoomboxStorageInventory storage;
	private final ItemStack boombox;
	private final EntityRef toPlayFrom;
	private final int unlockedSlots;
	private final Layer controls = new Layer(CONTROL_ROWS, WIDTH);
	/** Ticks left until a playing song restarts with a changed speed or volume; -1 when none is due. */
	private int restartIn = -1;
	private Object drawnState;

	public static BoomboxGui create(ServerPlayer player, ItemStack boombox, EntityRef toPlayFrom) {
		return new BoomboxGui(player, boombox, toPlayFrom, toPlayFrom::onUpdate);
	}

	public BoomboxGui(
			ServerPlayer player, ItemStack boombox, EntityRef toPlayFrom, Runnable onUpdate
	) {
		super(menuType(boombox), player, false);

		this.boombox = boombox;
		this.toPlayFrom = toPlayFrom;
		this.unlockedSlots = Math.min(
				boombox.getOrDefault(Components.STORAGE_SIZE, 0), (MAX_ROWS - CONTROL_ROWS) * WIDTH);
		this.inventory = new BoomboxInventory(boombox, onUpdate);
		this.storage = new BoomboxStorageInventory(boombox, onUpdate);

		// Font magic: a = back 8, b = background glyph, c = back to the title start (see better-pets' TrustPlayerSelector).
		var fontMagic = Component.literal("abc").withStyle(style -> style.withFont(MENU_FONT).withColor(ChatFormatting.WHITE));
		this.setTitle(Component.empty().append(fontMagic).append(Component.translatable("portable_jukebox.gui")));

		var filler = icon(Items.STAINED_GLASS_PANE.gray(), Component.literal(""))
				.set(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(true, ReferenceSortedSets.emptySet()))
				.build();
		for (int i = 0; i < getSize(); i++) {
			this.setSlot(i, filler);
		}

		controls.setSlot(DISC_ROW * WIDTH + DISC_COLUMN, new Slot(this.inventory, 0, 0, 0) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return stack.has(DataComponents.JUKEBOX_PLAYABLE);
			}

			@Override
			protected void onSwapCraft(int amount) {
				BoomboxItem.stop(boombox, player.level());
			}
		});
		this.addLayer(controls, 0, 0);
		redraw();

		if (unlockedSlots > 0) {
			Layer storageLayer = new Layer((unlockedSlots + WIDTH - 1) / WIDTH, WIDTH);
			for (int i = 0; i < unlockedSlots; i++) {
				storageLayer.setSlot(i, new Slot(storage, i, 0, 0) {
					@Override
					public boolean mayPlace(ItemStack stack) {
						return stack.has(DataComponents.JUKEBOX_PLAYABLE);
					}
				});
			}
			this.addLayer(storageLayer, 0, CONTROL_ROWS);
		}
	}

	private static int storageRows(ItemStack boombox) {
		return (boombox.getOrDefault(Components.STORAGE_SIZE, 0) + WIDTH - 1) / WIDTH;
	}

	private static boolean hasConfiguration(ItemStack boombox) {
		return boombox.has(Components.VOLUME_BUTTONS) || boombox.has(Components.SPEED_BUTTONS);
	}

	private static MenuType<?> menuType(ItemStack boombox) {
		return switch (Math.min(CONTROL_ROWS + storageRows(boombox), MAX_ROWS)) {
			case 2 -> MenuType.GENERIC_9x2;
			case 3 -> MenuType.GENERIC_9x3;
			case 4 -> MenuType.GENERIC_9x4;
			case 5 -> MenuType.GENERIC_9x5;
			default -> MenuType.GENERIC_9x6;
		};
	}

	@Override
	public boolean onAnyClick(int index, ClickType type, ContainerInput action) {
		int storageSlot = index - CONTROL_ROWS * WIDTH;
		if (
			storageSlot >= 0 && storageSlot < unlockedSlots &&
			action == ContainerInput.PICKUP && type == ClickType.MOUSE_RIGHT &&
			getPlayer().containerMenu.getCarried().isEmpty()
		) {
			var stored = storage.getItem(storageSlot);
			PlayerInventoryHelper.syncHandStack(getPlayer(), InteractionHand.OFF_HAND);
			if (!stored.isEmpty()) {
				var current = inventory.getItem(0);
				storage.setItem(storageSlot, current);
				inventory.setItem(0, stored);
				inventory.setChanged();
				restartIn = -1;
				BoomboxItem.play(boombox, toPlayFrom);
				return false;
			}
		}
		return super.onAnyClick(index, type, action);
	}

	// A throwing callback would leave the client out of sync, so log it and redraw.
	private void act(Runnable action) {
		try {
			var player = getPlayer();
			player.connection.send(new ClientboundSoundPacket(
					SoundEvents.UI_BUTTON_CLICK, SoundSource.UI, player.getX(), player.getY(), player.getZ(),
					0.6f, 1.0f, player.getRandom().nextLong()));
			action.run();
		} catch (RuntimeException e) {
			Boombox.LOGGER.error("Boombox menu action failed", e);
		} finally {
			redraw();
		}
	}

	private Object stateKey() {
		return List.of(
				isPlaying(),
				Objects.toString(boombox.get(Components.AUTOPLAY)),
				boombox.has(Components.SHUFFLE),
				Objects.toString(boombox.get(Components.CURRENT_SLOT)),
				boombox.getOrDefault(Components.BOOMBOX_CONFIGURATION, BoomboxConfiguration.DEFAULT),
				inventory.getItem(0).isEmpty(),
				hasStoredDiscs()
		);
	}

	private boolean hasStoredDiscs() {
		for (int i = 0; i < unlockedSlots; i++) {
			if (!storage.getItem(i).isEmpty()) return true;
		}
		return false;
	}

	private void redraw() {
		drawnState = stateKey();
		var config = boombox.getOrDefault(Components.BOOMBOX_CONFIGURATION, BoomboxConfiguration.DEFAULT);
		boolean hasDisc = !inventory.getItem(0).isEmpty();
		boolean hasStored = hasStoredDiscs();

		if (isPlaying()) {
			controls.setSlot(WIDTH + PLAY_COLUMN, control("stop", Component.translatable("portable_jukebox.gui.stop")).build(), () -> act(() -> {
				restartIn = -1;
				BoomboxItem.stop(boombox, getPlayer().level());
			}));
		} else if (hasDisc || hasStored) {
			controls.setSlot(WIDTH + PLAY_COLUMN, control("play", Component.translatable("portable_jukebox.gui.play")).build(), () -> act(() -> {
				restartIn = -1;
				if (inventory.getItem(0).isEmpty()) {
					Autoplay.playFromStorage(boombox, toPlayFrom, getPlayer().getRandom());
				} else {
					BoomboxItem.play(boombox, toPlayFrom);
				}
			}));
		} else {
			controls.setSlot(WIDTH + PLAY_COLUMN, control("play_disabled", Component.translatable("portable_jukebox.gui.play"))
					.set(DataComponents.LORE, lore(Component.translatable("portable_jukebox.gui.play.empty").withStyle(ChatFormatting.DARK_GRAY)))
					.build(), () -> act(() -> { }));
		}

		if (unlockedSlots > 0) {
			var skip = control(hasStored ? "skip" : "skip_disabled", Component.translatable("portable_jukebox.gui.skip"));
			skip.set(DataComponents.LORE, lore(hasStored
					? Component.translatable("portable_jukebox.gui.skip.description").withStyle(ChatFormatting.GRAY)
					: Component.translatable("portable_jukebox.gui.skip.empty").withStyle(ChatFormatting.DARK_GRAY)));
			controls.setSlot(WIDTH + SKIP_COLUMN, skip.build(), () -> act(() -> {
				restartIn = -1;
				if (hasStored) Autoplay.playFromStorage(boombox, toPlayFrom, getPlayer().getRandom());
			}));
		}

		if (boombox.has(Components.SHUFFLE_TOGGLE)) {
			boolean on = boombox.has(Components.SHUFFLE);
			controls.setSlot(SHUFFLE_COLUMN, control(!hasStored && !on ? "shuffle_disabled" : "shuffle",
							Component.translatable("portable_jukebox.gui.shuffle"))
							.set(DataComponents.LORE, lore(
									Component.translatable("portable_jukebox.gui.shuffle.description").withStyle(ChatFormatting.GRAY),
									Component.translatable(on ? "portable_jukebox.gui.shuffle.on" : "portable_jukebox.gui.shuffle.off")
											.withStyle(on ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY)))
							.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, on)
							.build(),
					() -> act(() -> {
						if (hasStored || on) Autoplay.toggleShuffle(boombox);
					}));
		}

		if (boombox.has(Components.PLAYLIST_TOGGLE)) {
			boolean on = boombox.has(Components.AUTOPLAY);
			List<Component> list = new ArrayList<>();
			list.add(Component.translatable("portable_jukebox.gui.playlist.description").withStyle(ChatFormatting.GRAY));
			if (on && !boombox.has(Components.SHUFFLE)) {
				var stored = BoomboxItem.getStorageList(boombox);
				var currentSlot = boombox.get(Components.CURRENT_SLOT);
				Autoplay.FORWARDS.pickSlot(
						stored, currentSlot != null ? OptionalInt.of(currentSlot) : OptionalInt.empty(), getPlayer().getRandom()
				).stream().mapToObj(stored::get).findAny()
						.map(disc -> disc.get(DataComponents.JUKEBOX_PLAYABLE))
						.ifPresent(disc -> list.add(Component.translatable(
								"portable_jukebox.gui.playlist.next_up", disc.song().value().description()
						).withStyle(ChatFormatting.AQUA)));
			}
			list.add(Component.translatable(on ? "portable_jukebox.gui.playlist.on" : "portable_jukebox.gui.playlist.off")
					.withStyle(on ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
			controls.setSlot(PLAYLIST_COLUMN, control(!hasStored && !on ? "playlist_disabled" : "playlist",
							Component.translatable("portable_jukebox.gui.playlist"))
							.set(DataComponents.LORE, lore(list))
							.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, on)
							.build(),
					() -> act(() -> {
						if (hasStored || on) Autoplay.togglePlaylist(boombox);
					}));
		}

		if (boombox.has(Components.VOLUME_BUTTONS)) {
			boolean boosted = BoomboxConfiguration.isRangeBoosted(config.volume());
			var name = volumeName(config.volume());
			controls.setSlot(VOLUME_DISPLAY_COLUMN, control("volume", name)
							.set(DataComponents.LORE, lore(
									Component.translatable("portable_jukebox.gui.volume.range", BoomboxConfiguration.hearingRange(config.volume())),
									Component.translatable("portable_jukebox.gui.restarts_song"),
									Component.translatable("portable_jukebox.gui.reset").withStyle(ChatFormatting.DARK_GRAY)))
							.build(),
					() -> act(() -> configure(c -> c.withVolume(BoomboxConfiguration.DEFAULT.volume()))));
			boolean atMin = config.volumeDown().equals(config);
			boolean atMax = config.volumeUp().equals(config);
			controls.setSlot(WIDTH + VOLUME_MINUS_COLUMN, step(false, atMin,
							boosted ? "portable_jukebox.gui.range.down" : "portable_jukebox.gui.volume.down", name),
					() -> act(() -> configure(BoomboxConfiguration::volumeDown)));
			controls.setSlot(WIDTH + VOLUME_PLUS_COLUMN, step(true, atMax,
							config.volume() >= 1.0f ? "portable_jukebox.gui.range.up" : "portable_jukebox.gui.volume.up", name),
					() -> act(() -> configure(BoomboxConfiguration::volumeUp)));
		}

		if (boombox.has(Components.SPEED_BUTTONS)) {
			var name = Component.translatable("portable_jukebox.gui.speed", BoomboxConfiguration.formatSpeed(config.speed()));
			controls.setSlot(SPEED_DISPLAY_COLUMN, control("speed", name)
							.set(DataComponents.LORE, lore(
									Component.translatable("portable_jukebox.gui.speed.description"),
									Component.translatable("portable_jukebox.gui.restarts_song"),
									Component.translatable("portable_jukebox.gui.reset").withStyle(ChatFormatting.DARK_GRAY)))
							.build(),
					() -> act(() -> configure(c -> c.withSpeed(BoomboxConfiguration.DEFAULT.speed()))));
			controls.setSlot(WIDTH + SPEED_MINUS_COLUMN, step(false, config.speedDown().equals(config), "portable_jukebox.gui.speed.down", name),
					() -> act(() -> configure(BoomboxConfiguration::speedDown)));
			controls.setSlot(WIDTH + SPEED_PLUS_COLUMN, step(true, config.speedUp().equals(config), "portable_jukebox.gui.speed.up", name),
					() -> act(() -> configure(BoomboxConfiguration::speedUp)));
		}
	}

	/**
	 * "Volume: 70%", or above full loudness, where turning it up only adds range, "Volume: 100% + 3x range".
	 * The name, not just the lore, so the change shows when the button is clicked.
	 */
	private static Component volumeName(float volume) {
		var loudness = BoomboxConfiguration.formatVolume(volume);
		if (!BoomboxConfiguration.isRangeBoosted(volume)) {
			return Component.translatable("portable_jukebox.gui.volume", loudness);
		}
		return Component.translatable("portable_jukebox.gui.volume.boosted", loudness, BoomboxConfiguration.formatRangeBoost(volume))
				.withStyle(ChatFormatting.GOLD);
	}

	/**
	 * Changes the speed or volume. A song already playing can't change its sound mid-way (Minecraft
	 * has no way to), so it restarts with the new one, {@link #RESTART_DELAY} ticks after the last click.
	 */
	private void configure(UnaryOperator<BoomboxConfiguration> change) {
		if (!hasConfiguration(boombox)) return;
		var config = boombox.getOrDefault(Components.BOOMBOX_CONFIGURATION, BoomboxConfiguration.DEFAULT);
		var changed = change.apply(config);
		if (changed.equals(config)) return;
		boombox.set(Components.BOOMBOX_CONFIGURATION, changed);
		inventory.setChanged();
		if (isPlaying()) {
			restartIn = RESTART_DELAY;
		}
	}

	private boolean isPlaying() {
		return boombox.has(Components.BOOMBOX_ENTITY);
	}

	/** Restarts the playing song with the new speed and volume, if it's still playing. */
	private void applyRestart() {
		restartIn = -1;
		if (isPlaying()) {
			BoomboxItem.play(boombox, toPlayFrom);
		}
	}

	private static ItemStack step(boolean up, boolean atLimit, String name, Component value) {
		return control(
				(up ? "plus" : "minus") + (atLimit ? "_disabled" : ""), Component.translatable(name)
		).set(DataComponents.LORE, lore(value)).build();
	}

	private static ItemLore lore(Component... lines) {
		return lore(Arrays.asList(lines));
	}

	private static ItemLore lore(Collection<Component> lines) {
		var all = new ArrayList<Component>();
		for (var line : lines) {
			all.add(
				Component.literal("").withStyle(ChatFormatting.GRAY).withStyle(
					style -> style.withItalic(false)
				).append(line)
			);
		}
		return new ItemLore(all);
	}

	private static Icon control(String icon, Component name) {
		return icon(Items.PAPER, name)
				.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath("portable_jukebox", "gui/" + icon));
	}

	private static Icon icon(Item item, Component name) {
		return new Icon(item, DataComponentPatch.builder().set(DataComponents.ITEM_NAME, name));
	}

	/** A display item for the menu: an item and the components it shows with. */
	private record Icon(Item item, DataComponentPatch.Builder components) {
		<T> Icon set(DataComponentType<T> type, T value) {
			components.set(type, value);
			return this;
		}

		ItemStack build() {
			return new ItemStack(item.builtInRegistryHolder(), 1, components.build());
		}
	}

	@Override
	public void onTick() {
		if (inventory.getBoombox().isEmpty()) {
			close();
			return;
		}
		if (restartIn > 0 && --restartIn == 0) {
			applyRestart();
		}
		if (!stateKey().equals(drawnState)) {
			redraw();
		}
		super.onTick();
	}

	/** Closing the menu right after a change still restarts the song with it. */
	@Override
	public void onRemoved() {
		if (restartIn > 0) {
			applyRestart();
		}
		super.onRemoved();
	}
}
