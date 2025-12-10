package nu.metacraft.faster_minecarts;

import com.mojang.serialization.Codec;
import eu.pb4.polymer.core.api.other.PolymerComponent;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Unit;

import java.util.function.UnaryOperator;

public class MinecartComponents {

	public static final DataComponentType<Boolean> SPEED_UPGRADE = register(
			"speed_upgrade", builder -> builder.persistent(
					Codec.withAlternative(Codec.BOOL, Unit.CODEC, u -> true)
			)
	);

	public static final DataComponentType<Double> MAX_SPEED = register(
			"max_speed", builder -> builder.persistent(Codec.DOUBLE)
	);

	public static final DataComponentType<Double> MAX_SPEED_UNDERWATER = register(
			"max_speed_underwater", builder -> builder.persistent(Codec.DOUBLE)
	);

	public static final DataComponentType<Double> ACCELERATION = register(
			"acceleration", builder -> builder.persistent(Codec.DOUBLE)
	);

	public static final DataComponentType<Double> SLOWDOWN = register(
			"slowdown", builder -> builder.persistent(Codec.DOUBLE)
	);

	public static final DataComponentType<Double> SLOWDOWN_WITH_PASSENGER = register(
			"slowdown_with_passenger", builder -> builder.persistent(Codec.DOUBLE)
	);

	public static final DataComponentType<Float> ITEM_SLOWDOWN_MODIFIER = register(
			"item_slowdown_modifier", builder -> builder.persistent(Codec.FLOAT)
	);

	public static final DataComponentType<Double> UNDERWATER_SLOWDOWN = register(
			"underwater_slowdown", builder -> builder.persistent(Codec.DOUBLE)
	);


	public static void init() {

	}

	protected static <T> DataComponentType<T> register(String id, UnaryOperator<DataComponentType.Builder<T>> builderOperator) {
		var entry = Registry.register(
				BuiltInRegistries.DATA_COMPONENT_TYPE, FasterMinecarts.getID(id),
				builderOperator.apply(DataComponentType.builder()).build()
		);
		PolymerComponent.registerDataComponent(entry);
		return entry;
	}

}
