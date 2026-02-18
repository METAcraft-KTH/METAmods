package nu.metacraft.portable_jukebox.item.components;

import eu.pb4.polymer.core.api.other.PolymerComponent;
import nu.metacraft.portable_jukebox.PortableJukebox;

import java.util.function.UnaryOperator;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

public class Components {

	//Careful, this is used by a datafixer!
	public static final DataComponentType<ItemStack> PORTABLE_JUKEBOX = register(
			"portable_jukebox", component -> component.persistent(ItemStack.CODEC)
	);

	public static final DataComponentType<PortableJukeboxEntityEntry> PORTABLE_JUKEBOX_ENTITY = register(
			"portable_jukebox_entity", component -> component.persistent(PortableJukeboxEntityEntry.CODEC)
	);

	public static final DataComponentType<PortableJukeboxConfiguration> PORTABLE_JUKEBOX_CONFIGURATION = register(
			"portable_jukebox_configuration", component -> component.persistent(PortableJukeboxConfiguration.CODEC)
	);



	public static void init() {

	}

	private static <T> DataComponentType<T> register(String id, UnaryOperator<DataComponentType.Builder<T>> componentBuilder) {
		var component = componentBuilder.apply(DataComponentType.builder()).build();
		PolymerComponent.registerDataComponent(component);
		return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, PortableJukebox.getID(id), component);
	}
}
