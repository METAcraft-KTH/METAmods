package nu.metacraft.simplecustomfeatures.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.state.BlockState;

@Mixin(PoiTypes.class)
public interface AccessorPointOfInterestTypes {

	@Accessor("TYPE_BY_STATE")
	static Map<BlockState, Holder<PoiType>> getStatesToTypeMap() {
		throw new IllegalStateException("Mixin failure");
	}

	@Invoker
	static void callRegisterBlockStates(Holder<PoiType> poiTypeEntry, Set<BlockState> states) {

	}

}
