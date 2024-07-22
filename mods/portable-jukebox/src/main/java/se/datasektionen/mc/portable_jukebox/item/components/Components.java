package se.datasektionen.mc.portable_jukebox.item.components;

import eu.pb4.polymer.core.api.other.PolymerComponent;
import net.minecraft.component.ComponentType;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import se.datasektionen.mc.portable_jukebox.PortableJukebox;

import java.util.function.UnaryOperator;

public class Components {

	public static final ComponentType<ItemStack> PORTABLE_JUKEBOX = register(
			"portable_jukebox", component -> component.codec(ItemStack.UNCOUNTED_CODEC)
	);

	public static final ComponentType<PortableJukeboxEntityEntry> PORTABLE_JUKEBOX_ENTITY = register(
			"portable_jukebox_entity", component -> component.codec(PortableJukeboxEntityEntry.CODEC)
	);

	public static final ComponentType<PortableJukeboxConfiguration> PORTABLE_JUKEBOX_CONFIGURATION = register(
			"portable_jukebox_configuration", component -> component.codec(PortableJukeboxConfiguration.CODEC)
	);



	public static void init() {

	}

	private static <T> ComponentType<T> register(String id, UnaryOperator<ComponentType.Builder<T>> componentBuilder) {
		var component = componentBuilder.apply(ComponentType.builder()).build();
		PolymerComponent.registerDataComponent(component);
		return Registry.register(Registries.DATA_COMPONENT_TYPE, PortableJukebox.getID(id), component);
	}
}
