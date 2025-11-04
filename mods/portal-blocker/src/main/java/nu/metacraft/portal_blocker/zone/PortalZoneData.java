package nu.metacraft.portal_blocker.zone;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import nu.metacraft.portal_blocker.Commands;
import nu.metacraft.portal_blocker.PortalState;
import nu.metacraft.portal_blocker.portal_type.PortalType;
import nu.metacraft.portal_blocker.portal_type.PortalTypeRegistry;
import nu.metacraft.zones.zone.data.ZoneData;
import nu.metacraft.zones.zone.data.ZoneDataType;

import java.util.*;
import java.util.function.Function;
import net.minecraft.util.StringRepresentable;

public class PortalZoneData extends ZoneData {

	public static final MapCodec<PortalZoneData> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.unboundedMap(
					PortalTypeRegistry.REGISTRY.byNameCodec(),
					PortalState.CODEC
			).fieldOf("portal_states").forGetter(data -> data.portalStates)
	).apply(instance, PortalZoneData::new));

	protected final Map<PortalType, PortalState> portalStates;

	public PortalZoneData(Map<PortalType, PortalState> affectedPortals) {
		this.portalStates = new HashMap<>(affectedPortals);
	}

	public BlockResult getBlockedState(PortalType type, PortalState.BlockingType blockingType) {
		if (portalStates.containsKey(type) && portalStates.get(type).isInState(blockingType)) {
			return portalStates.get(type).isBlocked(blockingType) ? BlockResult.BLOCKED : BlockResult.ALLOWED;
		}
		return BlockResult.DEFAULT;
	}

	public Map<PortalType, PortalState> getPortalStates() {
		return Collections.unmodifiableMap(portalStates);
	}

	public void setBlocking(PortalType portal, Commands.PortalBlockType blockingType, BlockResult isBlocking) {
		for (var type : blockingType.blockingTypes) {
			var state = this.portalStates.computeIfAbsent(portal, (k) -> PortalState.from());
			if (isBlocking == BlockResult.DEFAULT) {
				state.removeFromState(type);
			} else {
				state.setBlocked(type, isBlocking == BlockResult.BLOCKED);
			}
		}
		markDirty();
	}

	@Override
	public ZoneDataType<PortalZoneData> getType() {
		return ZoneDataPortalBlocker.PORTAL_DATA;
	}

	@Override
	public String toString() {
		return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
	}

	public enum BlockResult implements StringRepresentable {
		BLOCKED("block"),
		ALLOWED("allow"),
		DEFAULT("default");

		private static final Function<String, BlockResult> MAPPER = StringRepresentable.createNameLookup(BlockResult.values());

		private final String name;

		BlockResult(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return name;
		}

		@Override
		public String toString() {
			return getSerializedName();
		}

		public static Optional<BlockResult> fromString(String value) {
			return Optional.ofNullable(MAPPER.apply(value));
		}
	}
}
