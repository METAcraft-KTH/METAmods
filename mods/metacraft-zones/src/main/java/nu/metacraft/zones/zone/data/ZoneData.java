package nu.metacraft.zones.zone.data;

import com.mojang.serialization.Codec;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.text.Text;
import nu.metacraft.zones.zone.Zone;

public abstract class ZoneData {

	protected transient Zone zone;

	public final void setZone(Zone zone) {
		this.zone = zone;
	}

	public static final Codec<ZoneData> REGISTRY_CODEC = ZoneDataRegistry.REGISTRY.getCodec().dispatch(
			ZoneData::getType, ZoneDataType::codec
	);

	public void markDirty() {
		zone.markDirty();
	}

	public Zone getZone() {
		return zone;
	}

	public abstract ZoneDataType<? extends ZoneData> getType();

	protected Text toText() {
		return Text.literal(this.toString());
	}

	public Text toText(RegistryWrapper.WrapperLookup lookup) {
		return toText();
	}

}
