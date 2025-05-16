package se.datasektionen.mc.metacraft_season_4.item.components;

import eu.pb4.polymer.core.api.other.PolymerComponent;
import net.minecraft.component.ComponentType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import se.datasektionen.mc.metacraft_season_4.Season4;

import java.util.function.UnaryOperator;

public class Season4Components {

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
