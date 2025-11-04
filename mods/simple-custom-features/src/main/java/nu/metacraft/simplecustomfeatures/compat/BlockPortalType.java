package nu.metacraft.simplecustomfeatures.compat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import nu.metacraft.lib.util.METACodecs;
import nu.metacraft.portal_blocker.portal_type.PortalType;
import nu.metacraft.simplecustomfeatures.objects.BaseObject;
import nu.metacraft.simplecustomfeatures.objects.ObjectType;

import java.util.*;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Portal;

public class BlockPortalType implements BaseObject<PortalType> {

	private static final Map<Portal, PortalType> TYPES = new HashMap<>();

	public static final MapCodec<BlockPortalType> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Codec.withAlternative(
							METACodecs.RegistryDependent.PORTAL_CODEC.listOf(),
							METACodecs.RegistryDependent.PORTAL_CODEC, List::of
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
	public DataResult<PortalType> createObject(ResourceKey<PortalType> id) {
		return DataResult.success(new PortalType(
				portals::contains,
				data.blockedCreationMessage().orElse(null),
				data.blockedTravelMessage().orElse(null)
		));
	}

	@Override
	public void onUnregister(Holder<PortalType> entry) {
		for (var portal : portals) {
			TYPES.remove(portal, entry.value());
		}
	}

	@Override
	public void onRegistrationSuccess(Holder.Reference<PortalType> entry) {
		for (var portal : portals) {
			TYPES.put(portal, entry.value());
		}
	}
}
