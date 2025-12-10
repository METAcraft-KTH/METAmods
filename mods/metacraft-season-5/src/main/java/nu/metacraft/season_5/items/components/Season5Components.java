package nu.metacraft.season_5.items.components;

import eu.pb4.polymer.core.api.other.PolymerComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import nu.metacraft.season_5.METAcraftSeason5;

import java.util.function.UnaryOperator;

public class Season5Components {

	public static final DataComponentType<BlockPos> DRILL_POSITION = register(
			"drill_position", component -> component.networkSynchronized(BlockPos.STREAM_CODEC)
	);

	public static void init() {

	}

	private static <T> DataComponentType<T> register(String name, UnaryOperator<DataComponentType.Builder<T>> builder) {
		var c = Registry.register(
				BuiltInRegistries.DATA_COMPONENT_TYPE, METAcraftSeason5.getID(name),
				builder.apply(DataComponentType.builder()).build()
		);
		PolymerComponent.registerDataComponent(c);
		return c;
	}

}
