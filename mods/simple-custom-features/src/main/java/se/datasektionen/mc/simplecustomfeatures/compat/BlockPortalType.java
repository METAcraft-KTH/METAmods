package se.datasektionen.mc.simplecustomfeatures.compat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.Portal;
import net.minecraft.registry.entry.RegistryEntry;
import se.datasektionen.mc.metacraft_lib.util.ExtraCodecs;
import se.datasektionen.mc.portal_blocker.portal_type.PortalType;
import se.datasektionen.mc.simplecustomfeatures.objects.BaseObject;
import se.datasektionen.mc.simplecustomfeatures.objects.ObjectType;

import java.util.*;

public class BlockPortalType implements BaseObject<PortalType> {

	private static final Map<Portal, PortalType> TYPES = new HashMap<>();

	public static final MapCodec<BlockPortalType> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Codec.withAlternative(
							ExtraCodecs.RegistryDependent.PORTAL_CODEC.listOf(),
							ExtraCodecs.RegistryDependent.PORTAL_CODEC, List::of
					).xmap(
							blocks -> (Set<Portal>) new HashSet<>(blocks), ArrayList::new
					).fieldOf("portals").forGetter(t -> t.portals),
					PortalTypeData.CODEC.forGetter(t -> t.data)
			).apply(instance, BlockPortalType::new)
	);

	private final Set<Portal> portals;
	private final PortalTypeData data;

	public BlockPortalType(Set<Portal> portals, PortalTypeData data) {
		this.portals = portals;
		this.data = data;
	}

	public static PortalType getFromPortal(Portal portal) {
		return TYPES.get(portal);
	}

	@Override
	public ObjectType<? extends BaseObject<PortalType>, PortalType> getType() {
		return PortalBlockerCompat.BLOCK_PORTAL_TYPE;
	}

	@Override
	public DataResult<PortalType> createObject() {
		return DataResult.success(new PortalType(
				portals::contains,
				data.blockedCreationMessage().orElse(null),
				data.blockedTravelMessage().orElse(null)
		));
	}

	@Override
	public void onUnregister(RegistryEntry<PortalType> entry) {
		for (var portal : portals) {
			TYPES.remove(portal, entry.value());
		}
	}

	@Override
	public void onRegistrationSuccess(RegistryEntry.Reference<PortalType> entry) {
		for (var portal : portals) {
			TYPES.put(portal, entry.value());
		}
	}
}
