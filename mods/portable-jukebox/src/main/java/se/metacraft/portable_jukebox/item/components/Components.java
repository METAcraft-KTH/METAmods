package se.metacraft.portable_jukebox.item.components;

import com.mojang.serialization.Codec;
import eu.pb4.polymer.core.api.other.PolymerComponent;
import net.minecraft.world.item.component.ItemContainerContents;
import se.metacraft.portable_jukebox.upgrade.JukeboxUpgrades;
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

	/** Which upgrade tier (0 to 3) the jukebox has been raised to. See {@link JukeboxUpgrades}. */
	public static final DataComponentType<Integer> PORTABLE_JUKEBOX_TIER = register(
			"portable_jukebox_tier", component -> component.persistent(Codec.intRange(0, JukeboxUpgrades.MAX_TIER))
	);

	/** The music discs kept in the jukebox's storage rows, one per slot, slot order kept. */
	public static final DataComponentType<ItemContainerContents> PORTABLE_JUKEBOX_STORAGE = register(
			"portable_jukebox_storage", component -> component.persistent(ItemContainerContents.CODEC)
	);

	/** Present (true) while the jukebox is shuffling. See {@link se.metacraft.portable_jukebox.upgrade.JukeboxShuffle}. */
	public static final DataComponentType<Boolean> PORTABLE_JUKEBOX_SHUFFLE = register(
			"portable_jukebox_shuffle", component -> component.persistent(Codec.BOOL)
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

		public static void init() {

		}
	}
}
