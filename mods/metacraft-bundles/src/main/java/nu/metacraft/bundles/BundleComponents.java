package nu.metacraft.bundles;

import eu.pb4.polymer.core.api.other.PolymerComponent;
import net.minecraft.component.ComponentType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import nu.metacraft.lib.util.ExtraCodecs;
import org.apache.commons.lang3.math.Fraction;

import java.util.function.UnaryOperator;

public class BundleComponents {

	public static final ComponentType<Fraction> BUNDLE_SIZE_FACTOR = register(
			"bundle_size_factor", builder -> builder.codec(ExtraCodecs.POSITIVE_FRACTION_CODEC)
	);

	public static void init() {

	}


	protected static <T> ComponentType<T> register(String id, UnaryOperator<ComponentType.Builder<T>> builderOperator) {
		var entry = Registry.register(
				Registries.DATA_COMPONENT_TYPE, METAcraftBundles.getID(id),
				builderOperator.apply(ComponentType.builder()).build()
		);
		PolymerComponent.registerDataComponent(entry);
		return entry;
	}

}
