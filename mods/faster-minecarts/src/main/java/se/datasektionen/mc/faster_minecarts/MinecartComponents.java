package se.datasektionen.mc.faster_minecarts;

import com.mojang.serialization.Codec;
import eu.pb4.polymer.core.api.other.PolymerComponent;
import net.minecraft.component.ComponentType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Unit;

import java.util.function.UnaryOperator;

public class MinecartComponents {

	public static final ComponentType<Unit> SPEED_UPGRADE = register(
			"speed_upgrade", builder -> builder.codec(Codec.unit(Unit.INSTANCE))
	);

	public static final ComponentType<Double> MAX_SPEED = register(
			"max_speed", builder -> builder.codec(Codec.DOUBLE)
	);

	public static final ComponentType<Double> MAX_SPEED_UNDERWATER = register(
			"max_speed_underwater", builder -> builder.codec(Codec.DOUBLE)
	);

	public static final ComponentType<Double> ACCELERATION = register(
			"acceleration", builder -> builder.codec(Codec.DOUBLE)
	);

	public static final ComponentType<Double> SLOWDOWN = register(
			"slowdown", builder -> builder.codec(Codec.DOUBLE)
	);

	public static final ComponentType<Double> SLOWDOWN_WITH_PASSENGER = register(
			"slowdown_with_passenger", builder -> builder.codec(Codec.DOUBLE)
	);

	public static final ComponentType<Float> ITEM_SLOWDOWN_MODIFIER = register(
			"item_slowdown_modifier", builder -> builder.codec(Codec.FLOAT)
	);

	public static final ComponentType<Double> UNDERWATER_SLOWDOWN = register(
			"underwater_slowdown", builder -> builder.codec(Codec.DOUBLE)
	);


	public static void init() {

	}

	protected static <T> ComponentType<T> register(String id, UnaryOperator<ComponentType.Builder<T>> builderOperator) {
		var entry = Registry.register(
				Registries.DATA_COMPONENT_TYPE, FasterMinecarts.getID(id),
				builderOperator.apply(ComponentType.builder()).build()
		);
		PolymerComponent.registerDataComponent(entry);
		return entry;
	}

}
