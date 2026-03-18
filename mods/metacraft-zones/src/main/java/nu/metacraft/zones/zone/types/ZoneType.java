package nu.metacraft.zones.zone.types;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.zones.zone.Zone;
import nu.metacraft.zones.zone.ZoneRegistry;

public abstract class ZoneType {

	/**
	 * WARNING, this codec will require RegistryOps!
	 */
	public static Codec<ZoneType> REGISTRY_CODEC = ZoneRegistry.REGISTRY.byNameCodec().dispatch(
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

	public abstract InwardVector getInwardVector(Vec3 pos);

	public abstract ZoneType copy();

	public abstract ZoneRegistry.ZoneTypeType<?> getType();

	public record InwardVector(Vec3 vector, Vec3 target) {
		static final InwardVector ZERO = new InwardVector(Vec3.ZERO, Vec3.ZERO);

		public static InwardVector createFrom(Vec3 target, Vec3 pos) {
			return new InwardVector(target.subtract(pos).normalize(), target);
		}
	}
}
