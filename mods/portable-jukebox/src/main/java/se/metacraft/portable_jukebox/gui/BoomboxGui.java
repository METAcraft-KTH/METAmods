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
import net.minecraft.server.level.ServerPlayer;
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
import se.metacraft.lib.util.TaskScheduler;
import se.metacraft.portable_jukebox.item.BoomboxItem;
import se.metacraft.portable_jukebox.item.components.Autoplay;
import se.metacraft.portable_jukebox.item.components.Components;
import se.metacraft.portable_jukebox.item.components.BoomboxConfiguration;

import java.util.*;
import java.util.function.UnaryOperator;

/**
 * The portable boombox menu. It only shows what the boombox's tier has, and grows with it:
 * <ul>
 *     <li>Row 1, always: upgrade tier, the disc slot, Shuffle (tier 2), Play and Stop.</li>
 *     <li>Tier 2: a row of speed and volume controls.</li>
 *     <li>Always: the disc storage rows (1 row at tier 0, 4 rows from tier 1).</li>
 * </ul>
 * Left-clicking a stored disc with nothing on the cursor swaps it into the disc slot and plays it;
 * shift-clicking moves it to the player's inventory. Changing speed or volume while a song plays
 * restarts the song with the new sound, once the clicking stops.
 */
public class BoomboxGui extends LayeredGui {

	private static final int WIDTH = 9;

	private static final int DISC_SLOT_X = 3;
	private static final int SHUFFLE_X = 6;
	private static final int PLAY_X = 7;
	private static final int STOP_X = 8;
	private static final int SPEED_X = 1;
	private static final int VOLUME_X = 5;

	/** Ticks after the last speed or volume click before a playing song restarts with the new sound. */
	private static final int RESTART_DELAY = 15;

	private final BoomboxInventory inventory;
	private final BoomboxStorageInventory storage;
	private final ItemStack boombox;
	private final EntityRef toPlayFrom;
	private final int unlockedSlots;
	private final int storageStart;
	private final Layer topLayer = new Layer(1, WIDTH);
	private final Layer configurationLayer = new Layer(1, WIDTH);
	/** Ticks left until a playing song restarts with a changed speed or volume; -1 when none is due. */
	private int restartIn = -1;

	private OptionalInt currentSlot;

	public static BoomboxGui create(ServerPlayer player, ItemStack boombox, EntityRef toPlayFrom) {
		return new BoomboxGui(player, boombox, toPlayFrom, toPlayFrom::onUpdate);
	}

	private boolean updateCurrentSlot() {
		var s = boombox.get(Components.CURRENT_SLOT);
		var newCurrentSlot = s != null ? OptionalInt.of(s) : OptionalInt.empty();
		if (newCurrentSlot.equals(currentSlot)) return false;
		this.currentSlot = newCurrentSlot;
		return true;
	}

	public BoomboxGui(
			ServerPlayer player, ItemStack boombox, EntityRef toPlayFrom, Runnable onUpdate
	) {
		super(menuType(boombox), player, false);

		this.boombox = boombox;
		this.toPlayFrom = toPlayFrom;
		this.unlockedSlots = boombox.getOrDefault(Components.STORAGE_SIZE, 0);
		updateCurrentSlot();
		this.inventory = new BoomboxInventory(boombox, onUpdate);
		this.storage = new BoomboxStorageInventory(boombox, onUpdate);
		boolean configurable = hasConfiguration(boombox);
		this.storageStart = (configurable ? 2 : 1) * WIDTH;

		this.setTitle(Component.translatable(
				"portable_jukebox.gui"
		));

		setBackground(icon(Items.STAINED_GLASS_PANE.orange(), Component.literal(""))
				.set(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(true, ReferenceSortedSets.emptySet()))
				.build());

		topLayer.setSlot(DISC_SLOT_X, new Slot(this.inventory, 0, 0, 0) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return stack.has(DataComponents.JUKEBOX_PLAYABLE);
			}

			@Override
			protected void onSwapCraft(int amount) {
				BoomboxItem.stop(boombox, player.level());
			}
		});
		topLayer.setSlot(
				PLAY_X, icon(Items.WOOL.green(), Component.literal("Play")).build(),
				() -> {
					Autoplay.turnOff(boombox);
					restartIn = -1;
					BoomboxItem.play(boombox, toPlayFrom);
					drawPlaylist();
				}
		);
		topLayer.setSlot(
				STOP_X, icon(Items.WOOL.red(), Component.literal("Stop")).build(),
				() -> {
					Autoplay.turnOff(boombox);
					restartIn = -1;
					BoomboxItem.stop(boombox, player.level());
					boombox.remove(Components.CURRENT_SLOT);
					drawPlaylist();
				}
		);
		drawPlaylist();
		this.addLayer(topLayer, 0, 0);

		if (configurable) {
			drawConfiguration();
			this.addLayer(configurationLayer, 0, 1);
		}

		if (unlockedSlots > 0) {
			Layer storageLayer = new Layer(storageRows(boombox), WIDTH);
			for (int i = 0; i < unlockedSlots; i++) {
				storageLayer.setSlot(i, new Slot(storage, i, 0, 0) {
					@Override
					public boolean mayPlace(ItemStack stack) {
						return stack.has(DataComponents.JUKEBOX_PLAYABLE);
					}
				});
			}
			this.addLayer(storageLayer, 0, storageStart / WIDTH);
		}
	}

	private static boolean hasConfiguration(ItemStack boombox) {
		return boombox.has(Components.VOLUME_BUTTONS) || boombox.has(Components.SPEED_BUTTONS);
	}

	private static int storageRows(ItemStack boombox) {
		return (boombox.getOrDefault(Components.STORAGE_SIZE, 0) + WIDTH - 1) / WIDTH;
	}

	/** Just tall enough for the tier: the top row, the controls row from tier 2, and the storage rows. */
	private static MenuType<?> menuType(ItemStack boombox) {
		int rows = 1 + (hasConfiguration(boombox) ? 1 : 0) + storageRows(boombox);
		return switch (Math.min(rows, 6)) {
			case 1 -> MenuType.GENERIC_9x1;
			case 2 -> MenuType.GENERIC_9x2;
			case 3 -> MenuType.GENERIC_9x3;
			case 4 -> MenuType.GENERIC_9x4;
			case 5 -> MenuType.GENERIC_9x5;
			default -> MenuType.GENERIC_9x6;
		};
	}

	@Override
	public boolean onAnyClick(int index, ClickType type, ContainerInput action) {
		int storageSlot = index - storageStart;
		if (storageSlot >= 0 && storageSlot < unlockedSlots) {
			if (
				action == ContainerInput.SWAP &&
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
					drawPlaylist();
					return false;
				}
			} else {
				TaskScheduler.scheduleImmediately(
					getPlayer().level().getServer(),
					() -> {
						if (isOpen()) {
							drawPlaylist();
						}
					}
				);
			}
		}
		return super.onAnyClick(index, type, action);
	}

	/** The Shuffle button, from its tier only: lit while shuffling. */
	private void drawPlaylist() {
		var autoplay = boombox.get(Components.AUTOPLAY);
		int x = SHUFFLE_X;
		if (boombox.has(Components.SHUFFLE_TOGGLE)) {
			topLayer.setSlot(
				x--,
				icon(Items.WOOL.purple(), Component.translatable("portable_jukebox.gui.shuffle"))
					.set(
						DataComponents.LORE, lore(
							Component.translatable("portable_jukebox.gui.shuffle.description").withStyle(ChatFormatting.GRAY),
							Component.translatable(autoplay == Autoplay.RANDOM ? "portable_jukebox.gui.shuffle.on" : "portable_jukebox.gui.shuffle.off").withStyle(autoplay == Autoplay.RANDOM ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY)
						)
					)
					.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, autoplay == Autoplay.RANDOM)
					.build(),
				() -> {
					restartIn = -1;
					Autoplay.RANDOM.start(boombox, toPlayFrom, getPlayer().getRandom());
					drawPlaylist();
				}
			);
		}
		if (boombox.has(Components.PLAYLIST_TOGGLE)) {
			List<Component> list = new ArrayList<>();
			list.add(
				Component.translatable("portable_jukebox.gui.playlist.description").withStyle(ChatFormatting.GRAY)
			);
			if (autoplay != null && autoplay.deterministic()) {
				updateCurrentSlot();
				var storage = BoomboxItem.getStorageList(boombox);
				autoplay.pickSlot(
					storage,
					currentSlot,
					getPlayer().getRandom()
				).stream().mapToObj(
					storage::get
				).findAny().map(disc -> disc.get(DataComponents.JUKEBOX_PLAYABLE)).ifPresent(
					disc -> list.add(
						Component.translatable(
							"portable_jukebox.gui.playlist.next_up", disc.song().value().description()
						).withStyle(ChatFormatting.AQUA)
					)
				);
			}
			list.add(
				Component.translatable(
					autoplay == Autoplay.FORWARDS ? "portable_jukebox.gui.playlist.on" : "portable_jukebox.gui.playlist.off"
				).withStyle(autoplay == Autoplay.FORWARDS ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY)
			);
			topLayer.setSlot(
				x,
				icon(Items.WOOL.orange(), Component.translatable("portable_jukebox.gui.playlist"))
					.set(DataComponents.LORE, lore(list))
					.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, autoplay == Autoplay.FORWARDS)
					.build(),
				() -> {
					restartIn = -1;
					Autoplay.FORWARDS.start(boombox, toPlayFrom, getPlayer().getRandom());
					drawPlaylist();
				}
			);
		}
	}

	private void drawConfiguration() {
		var config = boombox.getOrDefault(Components.BOOMBOX_CONFIGURATION, BoomboxConfiguration.DEFAULT);

		if (boombox.has(Components.SPEED_BUTTONS)) {
			configurationLayer.setSlot(SPEED_X + 1, icon(Items.NOTE_BLOCK, Component.translatable("portable_jukebox.gui.speed", BoomboxConfiguration.formatSpeed(config.speed())))
					.set(DataComponents.LORE, lore(
						Component.translatable("portable_jukebox.gui.speed.description"),
						Component.translatable("portable_jukebox.gui.restarts_song"),
						Component.translatable("portable_jukebox.gui.reset").withStyle(ChatFormatting.DARK_GRAY)
					)).build(),
				() -> configure(c -> c.withSpeed(BoomboxConfiguration.DEFAULT.speed())));
			configurationLayer.setSlot(
				SPEED_X, button(
					false, "portable_jukebox.gui.speed.down",
					Component.translatable(
						"portable_jukebox.gui.speed", BoomboxConfiguration.formatSpeed(config.speed())
					)
				),
				() -> configure(BoomboxConfiguration::speedDown)
			);
			configurationLayer.setSlot(
				SPEED_X + 2,
				button(
					true, "portable_jukebox.gui.speed.up",
					Component.translatable(
						"portable_jukebox.gui.speed", BoomboxConfiguration.formatSpeed(config.speed())
					)
				),
				() -> configure(BoomboxConfiguration::speedUp)
			);
		}

		if (boombox.has(Components.VOLUME_BUTTONS)) {
			configurationLayer.setSlot(VOLUME_X + 1, icon(Items.BELL, volumeName(config.volume()))
					.set(DataComponents.LORE, lore(
						Component.translatable("portable_jukebox.gui.volume.range", BoomboxConfiguration.hearingRange(config.volume())),
						Component.translatable("portable_jukebox.gui.restarts_song"),
						Component.translatable("portable_jukebox.gui.reset").withStyle(ChatFormatting.DARK_GRAY)
					)).build(),
				() -> configure(c -> c.withVolume(BoomboxConfiguration.DEFAULT.volume())));
			// Past full loudness the volume buttons only change how far the song carries, so they say so.
			boolean boosted = BoomboxConfiguration.isRangeBoosted(config.volume());
			boolean fullyLoud = config.volume() >= 1.0f;
			configurationLayer.setSlot(
				VOLUME_X,
				button(
					false, boosted ? "portable_jukebox.gui.range.down" : "portable_jukebox.gui.volume.down",
					volumeName(config.volume())
				),
				() -> configure(BoomboxConfiguration::volumeDown)
			);
			configurationLayer.setSlot(
				VOLUME_X + 2,
				button(
					true, fullyLoud ? "portable_jukebox.gui.range.up" : "portable_jukebox.gui.volume.up",
					volumeName(config.volume())
				),
				() -> configure(BoomboxConfiguration::volumeUp)
			);
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
		drawConfiguration();
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

	private static ItemStack button(boolean up, String name, Component tooltip) {
		return icon(
			up ? Items.STAINED_GLASS.lime() : Items.STAINED_GLASS.red(), Component.translatable(name)
		).set(
			DataComponents.LORE, lore(tooltip)
		).build();
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

	private void setBackground(ItemStack background) {
		for (int i = 0; i < getSize(); i++) {
			this.setSlot(i, background);
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
		if (updateCurrentSlot()) {
			drawPlaylist();
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
