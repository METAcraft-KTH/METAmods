package nu.metacraft.zones.zone.types;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.zones.METAcraftZones;
import nu.metacraft.zones.util.ZoneCommandUtils;
import nu.metacraft.zones.ZoneManagementCommand;
import nu.metacraft.zones.ZoneManager;
import nu.metacraft.zones.zone.RealZone;
import nu.metacraft.zones.zone.ZoneRegistry;

import java.util.Optional;
import java.util.function.Function;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;

public class ZoneZone extends ZoneType {

	private final String zone;
	private double size;
	private RealZone zoneCache = null;

	public static final MapCodec<ZoneZone> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.STRING.fieldOf("zone").forGetter(zone -> zone.zone),
			Codec.DOUBLE.fieldOf("cachedSize").orElse(0.0).forGetter(zone -> zone.size)
	).apply(instance, ZoneZone::new));

	public static ArgumentBuilder<CommandSourceStack, ?> createCommand(
			ArgumentBuilder<CommandSourceStack, ?> builder, ZoneManagementCommand.ZoneAdder addZone
	) {
		return builder.then(
			ZoneCommandUtils.zone("zone").executes(ctx -> {
				String zone = ZoneCommandUtils.getZone(ctx, "zone").getName();
				return addZone.add(() -> new ZoneZone(zone), ctx);
			})
		);
	}

	public ZoneZone(String zone, double size) {
		this.zone = zone;
		this.size = size;
	}

	public ZoneZone(String zone) {
		this(zone, 0);
	}

	private final ThreadLocal<Boolean> checkingZone = ThreadLocal.withInitial(() -> false);

	//This is weird to help guard against stack overflows.
	protected <T> Function<Function<RealZone, T>, Optional<T>> getZone() {
		if (checkingZone.get()) {
			METAcraftZones.LOGGER.error("Warning, zone stack overflow detected in zone " + getZoneRef().getName() + "!");
			return getter -> Optional.empty();
		}
		if (zoneCache == null && getZoneRef() != null) {
			ZoneManager.getInstanceNoStackOverflow(getZoneRef().getWorld().getServer()).ifPresent(zoneManager -> {
				zoneCache = zoneManager.getZone(zone);
				if (zoneCache != null) {
					var correctSize = zoneCache.getZone().getSize();
					if (size != correctSize) {
						getZoneRef().markDirty();
					}
					size = correctSize;
				}
			});
		}
		return getter -> {
			checkingZone.set(true);
			var opt = Optional.ofNullable(zoneCache).map(getter);
			checkingZone.set(false);
			return opt;
		};
	}

	@Override
	public boolean contains(BlockPos pos) {
		return this.<Boolean>getZone().apply(zone -> zone.contains(getZoneRef().getDim(), pos)).orElse(false);
	}

	@Override
	public double getSize() {
		return this.<Double>getZone().apply(zone -> zone.getZone().getSize()).orElse(size);
	}

	@Override
	public InwardVector getInwardVector(Vec3 pos) {
		return this.<InwardVector>getZone().apply(zone -> zone.getZone().getInwardVector(pos)).orElse(InwardVector.ZERO);
	}

	@Override
	public ZoneType copy() {
		return new ZoneZone(zone);
	}

	@Override
	public String toString() {
		return "Zone[zone=" + zone + "]";
	}

	@Override
	public ZoneRegistry.ZoneTypeType<?> getType() {
		return ZoneRegistry.zone;
	}
}
