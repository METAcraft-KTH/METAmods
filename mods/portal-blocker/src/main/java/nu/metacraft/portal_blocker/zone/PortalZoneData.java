package nu.metacraft.portal_blocker.zone;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.NbtByte;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import nu.metacraft.portal_blocker.Commands;
import nu.metacraft.portal_blocker.PortalState;
import nu.metacraft.portal_blocker.portal_type.PortalType;
import nu.metacraft.portal_blocker.portal_type.PortalTypeRegistry;
import nu.metacraft.zones.zone.data.ZoneData;
import nu.metacraft.zones.zone.data.ZoneDataType;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PortalZoneData extends ZoneData {

	public static final MapCodec<PortalZoneData> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			PortalTypeRegistry.REGISTRY.getCodec().listOf().fieldOf("affected_portals").forGetter(
					portalZoneData -> portalZoneData.affectedPortals.stream().toList()
			),
			Codec.BYTE.xmap(
					num -> new PortalState().fromNBT(NbtByte.of(num)),
					state -> state.toNBT().byteValue()
			).fieldOf("blocking_state").forGetter(value -> value.blockingState)
	).apply(instance, PortalZoneData::new));

	protected final Set<PortalType> affectedPortals;
	protected PortalState blockingState;

	public PortalZoneData(List<PortalType> affectedPortals, PortalState blockingState) {
		this.affectedPortals = new HashSet<>(affectedPortals);
		this.blockingState = blockingState;
	}

	public BlockResult getBlockedState(PortalType type, PortalState.BlockingType blockingType) {
		if (affectedPortals.contains(type)) {
			return blockingState.isBlocked(blockingType) ? BlockResult.BLOCKED : BlockResult.ALLOWED;
		}
		return BlockResult.DEFAULT;
	}

	public void setBlocking(PortalState.BlockingType blockingType, boolean isBlocking) {
		this.blockingState.setBlocked(blockingType, isBlocking);
		markDirty();
	}

	public boolean isBlocking(PortalState.BlockingType blockingType) {
		return blockingState.isBlocked(blockingType);
	}

	public String getCommandStateMessage(String suffix) {
		return Commands.PortalBlockType.BOTH.getBlockingString(this::isBlocking, suffix);
	}

	public Collection<PortalType> getAffectedPortals() {
		return affectedPortals;
	}

	@Override
	public ZoneDataType<PortalZoneData> getType() {
		return ZoneDataPortalBlocker.PORTAL_DATA;
	}

	@Override
	public String toString() {
		return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
	}

	public enum BlockResult {
		BLOCKED,
		ALLOWED,
		DEFAULT
	}
}
