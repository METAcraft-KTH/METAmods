package se.metacraft.portable_jukebox.item.components;

import com.mojang.serialization.Codec;
import eu.pb4.polymer.core.api.other.PolymerComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.util.Unit;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.storage.loot.ContainerComponentManipulator;
import se.metacraft.portable_jukebox.PortableJukebox;

import java.util.function.UnaryOperator;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;

public class Components {

	//Careful, this is used by a datafixer!
	public static final DataComponentType<PortableJukeboxInvData> PORTABLE_JUKEBOX = register(
			"portable_jukebox", component -> component.persistent(PortableJukeboxInvData.CODEC)
	);

	public static final DataComponentType<PortableJukeboxEntityEntry> PORTABLE_JUKEBOX_ENTITY = register(
			"portable_jukebox_entity", component -> component.persistent(PortableJukeboxEntityEntry.CODEC)
	);

	public static final DataComponentType<PortableJukeboxConfiguration> PORTABLE_JUKEBOX_CONFIGURATION = register(
			"portable_jukebox_configuration", component -> component.persistent(PortableJukeboxConfiguration.CODEC)
	);

	public static final DataComponentType<Integer> STORAGE_SIZE = register(
		"storage_size", component -> component.persistent(Codec.INT)
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

	public static final DataComponentType<Unit> PITCH_BUTTONS = register(
		"pitch_buttons", component -> component.persistent(Unit.CODEC)
	);

	public static final DataComponentType<ItemContainerContents> PORTABLE_JUKEBOX_STORAGE = register(
		"storage", component -> component.persistent(ItemContainerContents.CODEC)
	);

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
		return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, PortableJukebox.getID(id), component);
	}

	public static class Manipulators {

		public static final ContainerComponentManipulator<PortableJukeboxInvData> PORTABLE_JUKEBOX = new ContainerComponentManipulator<>(Components.PORTABLE_JUKEBOX, PortableJukeboxInvData.EMPTY);
		public static final ContainerComponentManipulator<ItemContainerContents> PORTABLE_JUKEBOX_STORAGE = new ContainerComponentManipulator<>(Components.PORTABLE_JUKEBOX_STORAGE, ItemContainerContents.EMPTY);

		public static void init() {

		}
	}
}
