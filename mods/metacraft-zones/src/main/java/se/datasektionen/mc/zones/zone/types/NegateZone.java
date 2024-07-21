package se.datasektionen.mc.zones.zone.types;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.util.math.BlockPos;
import se.datasektionen.mc.zones.util.ZoneCommandUtils;
import se.datasektionen.mc.zones.ZoneManagementCommand;
import se.datasektionen.mc.zones.zone.Zone;
import se.datasektionen.mc.zones.zone.ZoneRegistry;

public class NegateZone extends ZoneType {

	protected final ZoneType zone;

	public static final MapCodec<NegateZone> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			REGISTRY_CODEC.fieldOf("zone").forGetter(zone -> zone.zone)
	).apply(instance, NegateZone::new));


	public static ArgumentBuilder<ServerCommandSource, ?> createCommand(
			ArgumentBuilder<ServerCommandSource, ?> builder, ZoneManagementCommand.ZoneAdder addZone
	) {
		return builder.then(
				ZoneCommandUtils.zoneType("zone").executes(ctx -> {
					addZone.add(() -> new NegateZone(ZoneCommandUtils.getZoneType(ctx, "zone")), ctx);
					return 1;
				})
		);
	}

	public NegateZone(ZoneType zone) {
		this.zone = zone;
		this.setZoneRef(zone.getZoneRef());
	}

	public ZoneType getZone() {
		return zone;
	}

	@Override
	public boolean contains(BlockPos pos) {
		return !zone.contains(pos);
	}

	@Override
	public void setZoneRef(Zone zone) {
		this.zone.setZoneRef(zone);
		super.setZoneRef(zone);
	}

	@Override
	public double getSize() {
		return zone.getSize();
	}

	@Override
	public ZoneType copy() {
		return new NegateZone(zone.copy());
	}

	@Override
	public String toString() {
		return "Negate:[" + zone + "]";
	}

	@Override
	public ZoneRegistry.ZoneTypeType<?> getType() {
		return ZoneRegistry.negate;
	}
}
