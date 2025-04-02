package se.datasektionen.mc.zones.zone.types;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.util.math.BlockPos;
import org.apache.commons.lang3.mutable.MutableObject;
import org.pcollections.PVector;
import org.pcollections.TreePVector;
import se.datasektionen.mc.zones.util.ZoneCommandUtils;
import se.datasektionen.mc.zones.ZoneManagementCommand;
import se.datasektionen.mc.zones.zone.Zone;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class CombinedZone extends ZoneType {


	protected AtomicReference<PVector<ZoneType>> zones;
	protected volatile double size;

	public static <T extends CombinedZone> MapCodec<T> getCodec(Function<List<ZoneType>, T> creator) {
		return RecordCodecBuilder.mapCodec(instance -> instance.group(
				REGISTRY_CODEC.listOf().fieldOf("zones").forGetter(zone -> zone.zones.get())
		).apply(instance, creator));
	}

	public static ArgumentBuilder<ServerCommandSource, ?> createCommand(
			ArgumentBuilder<ServerCommandSource, ?> builder, ZoneManagementCommand.ZoneAdder addZone,
			Function<List<ZoneType>, CombinedZone> creator
	) {
		Function<Integer, Command<ServerCommandSource>> commandExecution = zoneCount -> {
			return ctx -> {
				List<ZoneType> zones = new ArrayList<>(zoneCount);
				for (int i = 0; i < zoneCount; i++) {
					zones.add(ZoneCommandUtils.getZoneType(ctx, "zone" + i));
				}
				addZone.add(() -> creator.apply(zones), ctx);
				return 1;
			};
		};
		final int maxLength = 10;
		var currentPoint = ZoneCommandUtils.zoneType("zone" + maxLength).executes(commandExecution.apply(maxLength));
		for (int i = maxLength; i > 0; i--) {
			var next = ZoneCommandUtils.zoneType("zone" + (i-1)).executes(commandExecution.apply(i));
			next.then(currentPoint);
			currentPoint = next;
		}
		return builder.then(currentPoint);
	}

	public CombinedZone(List<ZoneType> zones) {
		this.zones = new AtomicReference<>(
				TreePVector.from(zones)
		);
		if (zones.isEmpty()) {
			throw new IllegalArgumentException("Don't create an empty combined zone!");
		}
		var ref = zones.getFirst().getZoneRef();
		if (ref != null) {
			this.setZoneRef(ref);
		}
	}

	protected abstract double getSize(Iterable<ZoneType> zones);
	protected abstract double updateSize(ZoneType newZone, UpdateDirection direction);

	@Override
	public boolean contains(BlockPos pos) {
		return combinedZoneContains(pos);
	}

	public abstract boolean combinedZoneContains(BlockPos pos);

	public void addZone(ZoneType zone) {
		zones.getAndUpdate(
				z -> z.plus(zone)
		);
		zone.setZoneRef(getZoneRef());
		size = updateSize(zone, UpdateDirection.ADD);
	}

	public ZoneType removeZone(int index) {
		MutableObject<ZoneType> zone = new MutableObject<>();
		zones.getAndUpdate(
				z -> {
					zone.setValue(z.get(index));
					return z.minus(index);
				}
		);

		size = updateSize(zone.getValue(), UpdateDirection.REMOVE);

		return zone.getValue();
	}

	public void forEach(Consumer<ZoneType> consumer) {
		zones.get().forEach(consumer);
	}

	public int zoneCount() {
		return zones.get().size();
	}

	public ZoneType getZone(int index) {
		return zones.get().get(index);
	}

	@Override
	public void setZoneRef(Zone zone) {
		super.setZoneRef(zone);
		for (ZoneType type : zones.get()) {
			type.setZoneRef(zone);
		}
		size = getSize(zones.get());
	}

	@Override
	public double getSize() {
		return size;
	}

	@Override
	public ZoneType copy() {
		return copy(zones.get().stream().map(ZoneType::copy).collect(Collectors.toList()));
	}

	@Override
	public String toString() {
		return getClass().getSimpleName().replace("Zone", "") + "[" + zones.get().stream().map(
				ZoneType::toString
		).collect(Collectors.joining(", ")) + "]";
	}

	protected abstract ZoneType copy(List<ZoneType> newZones);

	public enum UpdateDirection {
		ADD,
		REMOVE
	}
}
