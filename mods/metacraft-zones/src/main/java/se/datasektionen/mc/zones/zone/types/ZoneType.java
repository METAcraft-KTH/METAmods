package se.datasektionen.mc.zones.zone.types;

import com.mojang.serialization.Codec;
import net.minecraft.util.math.BlockPos;
import se.datasektionen.mc.zones.zone.Zone;
import se.datasektionen.mc.zones.zone.ZoneRegistry;

public abstract class ZoneType {

	/**
	 * WARNING, this codec will require RegistryOps!
	 */
	public static Codec<ZoneType> REGISTRY_CODEC = ZoneRegistry.REGISTRY.getCodec().dispatch(
			ZoneType::getType, ZoneRegistry.ZoneTypeType::codec
	);

	private Zone zoneRef;

	public void setZoneRef(Zone zone) {
		this.zoneRef = zone;
	}

	public Zone getZoneRef() {
		return zoneRef;
	}

	public abstract boolean contains(BlockPos pos);

	//Estimation of the zone size. Used to make smaller zones have higher priority by default. Does not have to be very accurate.
	public abstract double getSize();

	public abstract ZoneType copy();

	public abstract ZoneRegistry.ZoneTypeType<?> getType();
}
