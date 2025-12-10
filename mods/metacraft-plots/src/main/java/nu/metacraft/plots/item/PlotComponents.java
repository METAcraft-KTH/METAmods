package nu.metacraft.plots.item;

import eu.pb4.polymer.core.api.other.PolymerComponent;
import nu.metacraft.plots.METAcraftPlots;

import java.util.function.UnaryOperator;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;

public class PlotComponents {
	public static final DataComponentType<PlotKey.KeyComponent> KEY = register("key", builder -> builder.persistent(PlotKey.KeyComponent.CODEC));
	public static final DataComponentType<PlotKey.FriendlyNameComponent> FRIENDLY_NAME = register("friendly_name", builder -> builder.persistent(PlotKey.FriendlyNameComponent.CODEC));
	public static final DataComponentType<PlotKey.PlaceholderName> PLACEHOLDER_NAME = register("placeholder_name", builder -> builder.persistent(PlotKey.PlaceholderName.CODEC));
	public static final DataComponentType<PlotKey.CreationSecret> CREATION_SECRET = register("creation_secret", builder -> builder.persistent(PlotKey.CreationSecret.CODEC));
	public static final DataComponentType<PlotKey.KeysToRevoke> KEYS_TO_REVOKE = register("zones_to_remove", builder -> builder.persistent(PlotKey.KeysToRevoke.CODEC));

	public static void init() {

	}

	private static <T> DataComponentType<T> register(String id, UnaryOperator<DataComponentType.Builder<T>> builderOperator) {
		var component = builderOperator.apply(DataComponentType.builder()).build();
		PolymerComponent.registerDataComponent(component);
		return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, METAcraftPlots.getID(id), component);
	}
}
