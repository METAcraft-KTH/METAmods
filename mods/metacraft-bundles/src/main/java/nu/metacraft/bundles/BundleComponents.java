package nu.metacraft.bundles;

import eu.pb4.polymer.core.api.other.PolymerComponent;
import nu.metacraft.lib.util.METACodecs;
import org.apache.commons.lang3.math.Fraction;

import java.util.function.UnaryOperator;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;

public class BundleComponents {

	public static final DataComponentType<Fraction> BUNDLE_SIZE_FACTOR = register(
			"bundle_size_factor", builder -> builder.persistent(METACodecs.POSITIVE_FRACTION_CODEC)
	);

	public static void init() {

	}


	protected static <T> DataComponentType<T> register(String id, UnaryOperator<DataComponentType.Builder<T>> builderOperator) {
		var entry = Registry.register(
				BuiltInRegistries.DATA_COMPONENT_TYPE, METAcraftBundles.getID(id),
				builderOperator.apply(DataComponentType.builder()).build()
		);
		PolymerComponent.registerDataComponent(entry);
		return entry;
	}

}
