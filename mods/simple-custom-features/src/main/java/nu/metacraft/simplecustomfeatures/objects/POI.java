package nu.metacraft.simplecustomfeatures.objects;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import eu.pb4.polymer.core.api.utils.PolymerObject;
import nu.metacraft.simplecustomfeatures.mixin.AccessorPointOfInterestTypes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.block.state.BlockState;

public class POI implements BaseObject<PoiType> {

	public static final MapCodec<POI> CODEC = PolymerPOI.CODEC.xmap(
			POI::new, poi -> poi.type
	);

	private final PolymerPOI type;

	public POI(PolymerPOI type) {
		this.type = type;
	}

	@Override
	public ObjectType<? extends BaseObject<PoiType>, PoiType> getType() {
		return ObjectRegistry.POINT_OF_INTEREST;
	}

	@Override
	public DataResult<PoiType> createObject(ResourceKey<PoiType> id) {
		for (var state : type.matchingStates()) {
			if (AccessorPointOfInterestTypes.getStatesToTypeMap().containsKey(state)) {
				return DataResult.error(() -> state.toString() + " is already assigned to another POI");
			}
		}
		return DataResult.success(type);
	}

	@Override
	public void onRegistrationSuccess(Holder.Reference<PoiType> entry) {
		AccessorPointOfInterestTypes.callRegisterBlockStates(entry, entry.value().matchingStates());
	}

	@Override
	public void onUnregister(Holder<PoiType> entry) {
		AccessorPointOfInterestTypes.getStatesToTypeMap().keySet().removeAll(entry.value().matchingStates());
	}

	public static class PolymerPOI extends PoiType implements PolymerObject {

		public static final MapCodec<PolymerPOI> CODEC = RecordCodecBuilder.mapCodec(
				instance -> instance.group(
						BlockState.CODEC.listOf().<Set<BlockState>>xmap(
								HashSet::new, ArrayList::new
						).fieldOf("block_states").forGetter(PoiType::matchingStates),
						ExtraCodecs.NON_NEGATIVE_INT.fieldOf("ticket_count").forGetter(PoiType::maxTickets),
						ExtraCodecs.NON_NEGATIVE_INT.fieldOf("search_distance").forGetter(PoiType::validRange)
				).apply(instance, PolymerPOI::new)
		);

		public PolymerPOI(Set<BlockState> set, int i, int j) {
			super(set, i, j);
		}
	}
}
