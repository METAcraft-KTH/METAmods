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
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.TooltipDisplay;
import se.metacraft.lib.util.EntityRef;
import se.metacraft.portable_jukebox.item.PortableJukeboxItem;
import se.metacraft.portable_jukebox.item.components.Components;
import se.metacraft.portable_jukebox.item.components.PortableJukeboxConfiguration;
import se.metacraft.portable_jukebox.upgrade.JukeboxShuffle;
import se.metacraft.portable_jukebox.upgrade.JukeboxUpgrades;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * The portable jukebox menu. It only shows what the jukebox's tier has, and grows with it:
 * <ul>
 *     <li>Row 1, always: upgrade tier, the disc slot, Shuffle (tier 3), Play and Stop.</li>
 *     <li>Tier 3: a row of pitch and volume controls.</li>
 *     <li>Tier 1 and up: the disc storage rows (1 row at tier 1, 4 rows from tier 2).</li>
 * </ul>
 * Left-clicking a stored disc with nothing on the cursor swaps it into the disc slot and plays it;
 * shift-clicking moves it to the player's inventory. Changing pitch or volume while a song plays
 * restarts the song with the new sound, once the clicking stops.
 */
public class PortableJukeboxGui extends LayeredGui {

	private static final int WIDTH = 9;

	private static final int TIER_INFO_X = 0;
	private static final int DISC_SLOT_X = 3;
	private static final int SHUFFLE_X = 6;
	private static final int PLAY_X = 7;
	private static final int STOP_X = 8;
	private static final int PITCH_X = 1;
	private static final int VOLUME_X = 5;

	/** Ticks after the last pitch or volume click before a playing song restarts with the new sound. */
	private static final int RESTART_DELAY = 15;

	private final PortableJukeboxInventory portableJukebox;
	private final PortableJukeboxStorageInventory storage;
	private final ItemStack jukebox;
	private final EntityRef toPlayFrom;
	private final int tier;
	private final int unlockedSlots;
	private final int storageStart;
	private final Layer topLayer = new Layer(1, WIDTH);
	private final Layer configurationLayer = new Layer(1, WIDTH);
	/** Ticks left until a playing song restarts with a changed pitch or volume; -1 when none is due. */
	private int restartIn = -1;

	public static PortableJukeboxGui create(ServerPlayer player, ItemStack portableJukebox, EntityRef toPlayFrom) {
		return new PortableJukeboxGui(player, portableJukebox, toPlayFrom, toPlayFrom::onUpdate);
	}

	public PortableJukeboxGui(
			ServerPlayer player, ItemStack portableJukebox, EntityRef toPlayFrom, Runnable onUpdate
	) {
		super(menuType(JukeboxUpgrades.getTier(portableJukebox)), player, false);

		this.jukebox = portableJukebox;
		this.toPlayFrom = toPlayFrom;
		this.tier = JukeboxUpgrades.getTier(portableJukebox);
		this.unlockedSlots = JukeboxUpgrades.unlockedSlots(tier);
		this.portableJukebox = new PortableJukeboxInventory(portableJukebox, onUpdate);
		this.storage = new PortableJukeboxStorageInventory(portableJukebox, onUpdate);
		boolean configurable = hasConfiguration(tier);
		this.storageStart = (configurable ? 2 : 1) * WIDTH;

		this.setTitle(Component.translatable(
				"portable_jukebox.gui"
		));

		setBackground(icon(Items.STAINED_GLASS_PANE.orange(), Component.literal(""))
				.set(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(true, ReferenceSortedSets.emptySet()))
				.build());

		topLayer.setSlot(TIER_INFO_X, tierInfo());
		topLayer.setSlot(DISC_SLOT_X, new Slot(this.portableJukebox, 0, 0, 0) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return stack.has(DataComponents.JUKEBOX_PLAYABLE);
			}

			@Override
			protected void onSwapCraft(int amount) {
				PortableJukeboxItem.stop(portableJukebox, player.level());
			}
		});
		topLayer.setSlot(
				PLAY_X, icon(Items.WOOL.green(), Component.literal("Play")).build(),
				() -> {
					JukeboxShuffle.turnOff(portableJukebox);
					restartIn = -1;
					PortableJukeboxItem.play(portableJukebox, toPlayFrom);
					drawShuffle();
				}
		);
		topLayer.setSlot(
				STOP_X, icon(Items.WOOL.red(), Component.literal("Stop")).build(),
				() -> {
					JukeboxShuffle.turnOff(portableJukebox);
					restartIn = -1;
					PortableJukeboxItem.stop(portableJukebox, player.level());
					drawShuffle();
				}
		);
		drawShuffle();
		this.addLayer(topLayer, 0, 0);

		if (configurable) {
			drawConfiguration();
			this.addLayer(configurationLayer, 0, 1);
		}

		if (unlockedSlots > 0) {
			Layer storageLayer = new Layer(storageRows(tier), WIDTH);
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

	private static boolean hasConfiguration(int tier) {
		return tier >= JukeboxUpgrades.CONFIGURATION_TIER;
	}

	private static int storageRows(int tier) {
		return (JukeboxUpgrades.unlockedSlots(tier) + WIDTH - 1) / WIDTH;
	}

	/** Just tall enough for the tier: the top row, the controls row from tier 3, and the storage rows. */
	private static MenuType<?> menuType(int tier) {
		int rows = 1 + (hasConfiguration(tier) ? 1 : 0) + storageRows(tier);
		return switch (Math.min(rows, 6)) {
			case 1 -> MenuType.GENERIC_9x1;
			case 2 -> MenuType.GENERIC_9x2;
			case 3 -> MenuType.GENERIC_9x3;
			case 4 -> MenuType.GENERIC_9x4;
			case 5 -> MenuType.GENERIC_9x5;
			default -> MenuType.GENERIC_9x6;
		};
	}

	/** Left-click on a stored disc, empty cursor: swap it into the disc slot and play it. */
	@Override
	public boolean onAnyClick(int index, ClickType type, ContainerInput action) {
		int storageSlot = index - storageStart;
		if (
				type == ClickType.MOUSE_LEFT && action == ContainerInput.PICKUP &&
				storageSlot >= 0 && storageSlot < unlockedSlots &&
				getPlayer().containerMenu.getCarried().isEmpty()
		) {
			var stored = storage.getItem(storageSlot);
			if (!stored.isEmpty()) {
				var current = portableJukebox.getItem(0);
				storage.setItem(storageSlot, current);
				portableJukebox.setItem(0, stored);
				portableJukebox.setChanged();
				JukeboxShuffle.turnOff(jukebox);
				restartIn = -1;
				PortableJukeboxItem.play(jukebox, toPlayFrom);
				drawShuffle();
				return false;
			}
		}
		return super.onAnyClick(index, type, action);
	}

	/** The Shuffle button, from its tier only: lit while shuffling. */
	private void drawShuffle() {
		if (tier < JukeboxUpgrades.SHUFFLE_TIER) return;
		boolean on = JukeboxShuffle.isOn(jukebox);
		topLayer.setSlot(
				SHUFFLE_X,
				icon(Items.WOOL.purple(), Component.translatable("portable_jukebox.gui.shuffle"))
						.set(DataComponents.LORE, new ItemLore(List.of(
								Component.translatable("portable_jukebox.gui.shuffle.description").withStyle(ChatFormatting.GRAY),
								Component.translatable(on ? "portable_jukebox.gui.shuffle.on" : "portable_jukebox.gui.shuffle.off").withStyle(on ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY)
						)))
						.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, on)
						.build(),
				() -> {
					restartIn = -1;
					JukeboxShuffle.start(jukebox, toPlayFrom, getPlayer().getRandom());
					drawShuffle();
				}
		);
	}

	private void drawConfiguration() {
		var config = jukebox.getOrDefault(Components.PORTABLE_JUKEBOX_CONFIGURATION, PortableJukeboxConfiguration.DEFAULT);

		configurationLayer.setSlot(PITCH_X + 1, icon(Items.NOTE_BLOCK, Component.translatable("portable_jukebox.gui.pitch", PortableJukeboxConfiguration.formatPitch(config.pitch())))
				.set(DataComponents.LORE, lore(
						Component.translatable("portable_jukebox.gui.pitch.description"),
						Component.translatable("portable_jukebox.gui.restarts_song")
				)).build(),
				() -> configure(c -> c.withPitch(PortableJukeboxConfiguration.DEFAULT.pitch())));
		configurationLayer.setSlot(VOLUME_X + 1, icon(Items.BELL, volumeName(config.volume()))
				.set(DataComponents.LORE, lore(
						Component.translatable("portable_jukebox.gui.volume.range", PortableJukeboxConfiguration.hearingRange(config.volume())),
						Component.translatable("portable_jukebox.gui.restarts_song")
				)).build(),
				() -> configure(c -> c.withVolume(PortableJukeboxConfiguration.DEFAULT.volume())));

		configurationLayer.setSlot(PITCH_X, button(false, "portable_jukebox.gui.pitch.down"), () -> configure(PortableJukeboxConfiguration::pitchDown));
		configurationLayer.setSlot(PITCH_X + 2, button(true, "portable_jukebox.gui.pitch.up"), () -> configure(PortableJukeboxConfiguration::pitchUp));
		// Past full loudness the volume buttons only change how far the song carries, so they say so.
		boolean boosted = PortableJukeboxConfiguration.isRangeBoosted(config.volume());
		boolean fullyLoud = config.volume() >= 1.0f;
		configurationLayer.setSlot(VOLUME_X, button(false, boosted ? "portable_jukebox.gui.range.down" : "portable_jukebox.gui.volume.down"), () -> configure(PortableJukeboxConfiguration::volumeDown));
		configurationLayer.setSlot(VOLUME_X + 2, button(true, fullyLoud ? "portable_jukebox.gui.range.up" : "portable_jukebox.gui.volume.up"), () -> configure(PortableJukeboxConfiguration::volumeUp));
	}

	/**
	 * "Volume: 70%", or above full loudness, where turning it up only adds range, "Volume: 100% + 3x range".
	 * The name, not just the lore, so the change shows when the button is clicked.
	 */
	private static Component volumeName(float volume) {
		var loudness = PortableJukeboxConfiguration.formatVolume(volume);
		if (!PortableJukeboxConfiguration.isRangeBoosted(volume)) {
			return Component.translatable("portable_jukebox.gui.volume", loudness);
		}
		return Component.translatable("portable_jukebox.gui.volume.boosted", loudness, PortableJukeboxConfiguration.formatRangeBoost(volume))
				.withStyle(ChatFormatting.GOLD);
	}

	/**
	 * Changes the pitch or volume. A song already playing can't change its sound mid-way (Minecraft
	 * has no way to), so it restarts with the new one, {@link #RESTART_DELAY} ticks after the last click.
	 */
	private void configure(UnaryOperator<PortableJukeboxConfiguration> change) {
		if (!hasConfiguration(tier)) return;
		var config = jukebox.getOrDefault(Components.PORTABLE_JUKEBOX_CONFIGURATION, PortableJukeboxConfiguration.DEFAULT);
		var changed = change.apply(config);
		if (changed.equals(config)) return;
		jukebox.set(Components.PORTABLE_JUKEBOX_CONFIGURATION, changed);
		portableJukebox.setChanged();
		drawConfiguration();
		if (isPlaying()) {
			restartIn = RESTART_DELAY;
		}
	}

	private boolean isPlaying() {
		return jukebox.has(Components.PORTABLE_JUKEBOX_ENTITY);
	}

	/** Restarts the playing song with the new pitch and volume, if it's still playing. */
	private void applyRestart() {
		restartIn = -1;
		if (isPlaying()) {
			PortableJukeboxItem.play(jukebox, toPlayFrom);
		}
	}

	private ItemStack tierInfo() {
		var lines = new ArrayList<Component>();
		if (unlockedSlots > 0) {
			lines.add(Component.translatable("portable_jukebox.gui.tier.storage", unlockedSlots).withStyle(ChatFormatting.GRAY));
		}
		return icon(Items.DISC_FRAGMENT_5, Component.translatable("portable_jukebox.gui.tier", tier, JukeboxUpgrades.MAX_TIER))
				.set(DataComponents.LORE, new ItemLore(lines))
				.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, tier > 0)
				.build();
	}

	private static ItemStack button(boolean up, String name) {
		return icon(up ? Items.STAINED_GLASS_PANE.lime() : Items.STAINED_GLASS_PANE.red(), Component.translatable(name)).build();
	}

	private static ItemLore lore(Component... lines) {
		var all = new ArrayList<Component>();
		for (var line : lines) all.add(line.copy().withStyle(ChatFormatting.GRAY));
		all.add(Component.translatable("portable_jukebox.gui.reset").withStyle(ChatFormatting.DARK_GRAY));
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
		if (portableJukebox.getJukebox().isEmpty()) {
			close();
			return;
		}
		if (restartIn > 0 && --restartIn == 0) {
			applyRestart();
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
