package se.datasektionen.mc.simplecustomfeatures.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.poi.PointOfInterestType;
import net.minecraft.world.poi.PointOfInterestTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;
import java.util.Set;

@Mixin(PointOfInterestTypes.class)
public interface AccessorPointOfInterestTypes {

	@Accessor("POI_STATES_TO_TYPE")
	static Map<BlockState, RegistryEntry<PointOfInterestType>> getStatesToTypeMap() {
		throw new IllegalStateException("Mixin failure");
	}

	@Invoker
	static void callRegisterStates(RegistryEntry<PointOfInterestType> poiTypeEntry, Set<BlockState> states) {

	}

}
