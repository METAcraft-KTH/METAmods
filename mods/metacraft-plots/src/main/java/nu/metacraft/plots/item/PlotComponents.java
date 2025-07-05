package nu.metacraft.plots.item;

import eu.pb4.polymer.core.api.other.PolymerComponent;
import net.minecraft.component.ComponentType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import nu.metacraft.plots.METAcraftPlots;

import java.util.function.UnaryOperator;

public class PlotComponents {
	public static final ComponentType<PlotKey.KeyComponent> KEY = register("key", builder -> builder.codec(PlotKey.KeyComponent.CODEC));
	public static final ComponentType<PlotKey.FriendlyNameComponent> FRIENDLY_NAME = register("friendly_name", builder -> builder.codec(PlotKey.FriendlyNameComponent.CODEC));
	public static final ComponentType<PlotKey.PlaceholderName> PLACEHOLDER_NAME = register("placeholder_name", builder -> builder.codec(PlotKey.PlaceholderName.CODEC));
	public static final ComponentType<PlotKey.CreationSecret> CREATION_SECRET = register("creation_secret", builder -> builder.codec(PlotKey.CreationSecret.CODEC));
	public static final ComponentType<PlotKey.KeysToRevoke> KEYS_TO_REVOKE = register("zones_to_remove", builder -> builder.codec(PlotKey.KeysToRevoke.CODEC));

	public static void init() {

	}

	private static <T> ComponentType<T> register(String id, UnaryOperator<ComponentType.Builder<T>> builderOperator) {
		var component = builderOperator.apply(ComponentType.builder()).build();
		PolymerComponent.registerDataComponent(component);
		return Registry.register(Registries.DATA_COMPONENT_TYPE, METAcraftPlots.getID(id), component);
	}
}
