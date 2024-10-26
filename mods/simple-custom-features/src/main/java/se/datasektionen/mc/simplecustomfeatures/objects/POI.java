package se.datasektionen.mc.simplecustomfeatures.objects;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.dynamic.Codecs;
import net.minecraft.world.poi.PointOfInterestType;
import se.datasektionen.mc.simplecustomfeatures.mixin.AccessorPointOfInterestTypes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class POI implements BaseObject<PointOfInterestType> {

	public static final MapCodec<PointOfInterestType> POINT_OF_INTEREST_TYPE_CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					BlockState.CODEC.listOf().<Set<BlockState>>xmap(
							HashSet::new, ArrayList::new
					).fieldOf("block_states").forGetter(PointOfInterestType::blockStates),
					Codecs.NON_NEGATIVE_INT.fieldOf("ticket_count").forGetter(PointOfInterestType::ticketCount),
					Codecs.NON_NEGATIVE_INT.fieldOf("search_distance").forGetter(PointOfInterestType::searchDistance)
			).apply(instance, PointOfInterestType::new)
	);

	public static final MapCodec<POI> CODEC = POINT_OF_INTEREST_TYPE_CODEC.xmap(
			POI::new, poi -> poi.type
	);

	private final PointOfInterestType type;

	public POI(PointOfInterestType type) {
		this.type = type;
	}

	@Override
	public ObjectType<? extends BaseObject<PointOfInterestType>, PointOfInterestType> getType() {
		return ObjectRegistry.POINT_OF_INTEREST;
	}

	@Override
	public DataResult<PointOfInterestType> createObject(RegistryKey<PointOfInterestType> id) {
		for (var state : type.blockStates()) {
			if (AccessorPointOfInterestTypes.getStatesToTypeMap().containsKey(state)) {
				return DataResult.error(() -> state.toString() + " is already assigned to another POI");
			}
		}
		return DataResult.success(type);
	}

	@Override
	public void onRegistrationSuccess(RegistryEntry.Reference<PointOfInterestType> entry) {
		AccessorPointOfInterestTypes.callRegisterStates(entry, entry.value().blockStates());
	}

	@Override
	public void onUnregister(RegistryEntry<PointOfInterestType> entry) {
		AccessorPointOfInterestTypes.getStatesToTypeMap().keySet().removeAll(entry.value().blockStates());
	}
}
