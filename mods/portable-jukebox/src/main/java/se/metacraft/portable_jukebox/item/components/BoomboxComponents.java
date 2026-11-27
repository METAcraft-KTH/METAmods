package se.metacraft.portable_jukebox.item.components;

import com.mojang.serialization.Codec;
import eu.pb4.polymer.core.api.other.PolymerComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.util.Unit;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.storage.loot.ContainerComponentManipulator;
import se.metacraft.portable_jukebox.Boombox;

import java.util.function.UnaryOperator;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import se.metacraft.portable_jukebox.gui.BoomboxGui;

public class BoomboxComponents {

	//Careful, this is used by a datafixer!
	public static final DataComponentType<BoomboxInvData> BOOMBOX = register(
			"portable_jukebox", component -> component.persistent(BoomboxInvData.CODEC)
	);

	public static final DataComponentType<BoomboxEntityEntry> BOOMBOX_ENTITY = register(
			"portable_jukebox_entity", component -> component.persistent(BoomboxEntityEntry.CODEC)
	);

	public static final DataComponentType<BoomboxConfiguration> BOOMBOX_CONFIGURATION = register(
			"portable_jukebox_configuration", component -> component.persistent(BoomboxConfiguration.CODEC)
	);

	public static final DataComponentType<Integer> STORAGE_SIZE = register(
		"storage_size", component -> component.persistent(Codec.intRange(0, BoomboxGui.MAX_STORAGE_SIZE))
	);

	public static final DataComponentType<Unit> SHUFFLE_TOGGLE = register(
		"shuffle_toggle", component -> component.persistent(Unit.CODEC)
	);

	public static final DataComponentType<Unit> PLAYLIST_TOGGLE = register(
		"playlist_toggle", component -> component.persistent(Unit.CODEC)
	);

	public static final DataComponentType<Unit> VOLUME_BUTTONS = register(
		"volume_buttons", component -> component.persistent(Unit.CODEC)
	);

	public static final DataComponentType<Unit> SPEED_BUTTONS = register(
		"speed_buttons", component -> component.persistent(Unit.CODEC)
	);

	public static final DataComponentType<ItemContainerContents> BOOMBOX_STORAGE = register(
		"storage", component -> component.persistent(ItemContainerContents.CODEC)
	);

	/** Shuffle is on: the next disc is random. */
	public static final DataComponentType<Unit> SHUFFLE = register(
		"shuffle", component -> component.persistent(Unit.CODEC)
	);

	/** Playlist is on: the stored discs play on after each song. */
	public static final DataComponentType<Autoplay> AUTOPLAY = register(
		"autoplay", component -> component.persistent(Autoplay.CODEC)
	);

	public static final DataComponentType<Integer> CURRENT_SLOT = register(
		"current_slot", component -> component.networkSynchronized(ByteBufCodecs.INT)
	);

	public static void init() {
		Manipulators.init();
	}

	private static <T> DataComponentType<T> register(String id, UnaryOperator<DataComponentType.Builder<T>> componentBuilder) {
		var component = componentBuilder.apply(DataComponentType.builder()).build();
		PolymerComponent.registerDataComponent(component);
		return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Boombox.getID(id), component);
	}

	public static class Manipulators {

		public static final ContainerComponentManipulator<BoomboxInvData> BOOMBOX = new ContainerComponentManipulator<>(BoomboxComponents.BOOMBOX, BoomboxInvData.EMPTY);
		public static final ContainerComponentManipulator<ItemContainerContents> BOOMBOX_STORAGE = new ContainerComponentManipulator<>(BoomboxComponents.BOOMBOX_STORAGE, ItemContainerContents.EMPTY);

		public static void init() {

		}
	}
}
