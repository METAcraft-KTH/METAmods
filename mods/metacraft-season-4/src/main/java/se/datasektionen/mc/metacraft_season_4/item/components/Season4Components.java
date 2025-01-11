package se.datasektionen.mc.metacraft_season_4.item.components;

import com.mojang.serialization.Codec;
import eu.pb4.polymer.core.api.other.PolymerComponent;
import net.minecraft.component.ComponentType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.collection.DataPool;
import net.minecraft.util.math.intprovider.IntProvider;
import se.datasektionen.mc.metacraft_lib.util.helper.EntityHelper;
import se.datasektionen.mc.metacraft_season_4.Season4;

import java.util.function.UnaryOperator;

public class Season4Components {

	public static final ComponentType<DataPool<EntityHelper.SpawnEntry>> SPAWNS = register(
			"spawn_pool", builder -> builder.codec(EntityHelper.SpawnEntry.POOL_CODEC)
	);

	public static final ComponentType<Double> MAX_RANGE = register(
			"max_range", builder -> builder.codec(Codec.DOUBLE)
	);

	public static final ComponentType<IntProvider> TRY_COUNT = register(
			"try_count", builder -> builder.codec(IntProvider.NON_NEGATIVE_CODEC)
	);

	public static void init() {

	}

	private static <T> ComponentType<T> register(String id, UnaryOperator<ComponentType.Builder<T>> builderOperator) {
		var entry = Registry.register(
				Registries.DATA_COMPONENT_TYPE, Season4.getID(id),
				builderOperator.apply(ComponentType.builder()).build()
		);
		PolymerComponent.registerDataComponent(entry);
		return entry;
	}

}
