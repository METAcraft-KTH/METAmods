package se.datasektionen.mc.zones.zone.types;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.command.argument.ColumnPosArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ColumnPos;
import se.datasektionen.mc.zones.ZoneManagementCommand;
import se.datasektionen.mc.zones.zone.ZoneRegistry;

import static net.minecraft.server.command.CommandManager.argument;

public class RegionZone extends ZoneType {

	public static final MapCodec<RegionZone> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.INT.fieldOf("x1").forGetter(zone -> zone.minX),
			Codec.INT.fieldOf("z1").forGetter(zone -> zone.minZ),
			Codec.INT.fieldOf("x2").forGetter(zone -> zone.maxX),
			Codec.INT.fieldOf("z2").forGetter(zone -> zone.maxZ)
	).apply(instance, RegionZone::new));

	public static ArgumentBuilder<ServerCommandSource, ?> createCommand(
			ArgumentBuilder<ServerCommandSource, ?> builder, ZoneManagementCommand.ZoneAdder addZone
	) {
		return builder.then(
				argument("pos1", ColumnPosArgumentType.columnPos()).then(
						argument("pos2", ColumnPosArgumentType.columnPos()).executes(ctx -> {
							return addZone.add(() -> new RegionZone(
									ColumnPosArgumentType.getColumnPos(ctx,"pos1"),
									ColumnPosArgumentType.getColumnPos(ctx,"pos2")
							), ctx);
						})
				)
		);
	}

	public int minX;
	public int minZ;
	public int maxX;
	public int maxZ;

	public RegionZone(int x1, int z1, int x2, int z2) {
		this.minX = Math.min(x1, x2);
		this.minZ = Math.min(z1, z2);
		this.maxX = Math.max(x1, x2);
		this.maxZ = Math.max(z1, z2);
	}

	public RegionZone(ColumnPos one, ColumnPos two) {
		this(one.x(), one.z(), two.x(), two.z());
	}

	@Override
	public boolean contains(BlockPos pos) {
		return minX <= pos.getX() && maxX >= pos.getX() && minZ <= pos.getZ() && maxZ >= pos.getZ();
	}

	@Override
	public double getSize() {
		return ((double) maxX - minX) * ((double) maxZ - minZ) * getZoneRef().getWorld().getHeight();
	}

	@Override
	public ZoneType copy() {
		return new RegionZone(minX, minZ, maxX, maxZ);
	}

	@Override
	public ZoneRegistry.ZoneTypeType<? extends RegionZone> getType() {
		return ZoneRegistry.region;
	}

	@Override
	public String toString() {
		return "Region[" + "from:{x=" + minX + ", z=" + minZ + "}, to:{x=" + maxX + ", z=" + maxZ + "}" + "]";
	}
}
