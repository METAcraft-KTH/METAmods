package nu.metacraft.zones.zone.data;

import com.mojang.serialization.Codec;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import nu.metacraft.zones.zone.Zone;

public abstract class ZoneData {

	protected transient Zone zone;

	public final void setZone(Zone zone) {
		this.zone = zone;
	}

	public static final Codec<ZoneData> REGISTRY_CODEC = ZoneDataRegistry.REGISTRY.byNameCodec().dispatch(
			ZoneData::getType, ZoneDataType::codec
	);

	public void markDirty() {
		zone.markDirty();
	}

	public Zone getZone() {
		return zone;
	}

	public abstract ZoneDataType<? extends ZoneData> getType();

	protected Component toText() {
		return Component.literal(this.toString());
	}

	public Component toText(HolderLookup.Provider lookup) {
		return toText();
	}

}
