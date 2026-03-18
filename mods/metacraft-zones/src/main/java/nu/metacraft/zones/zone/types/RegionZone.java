package nu.metacraft.zones.zone.types;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.coordinates.ColumnPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ColumnPos;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.zones.ZoneManagementCommand;
import nu.metacraft.zones.util.ZoneVectorHelper;
import nu.metacraft.zones.zone.ZoneRegistry;

import static net.minecraft.commands.Commands.argument;

public class RegionZone extends ZoneType {

	public static final MapCodec<RegionZone> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.INT.fieldOf("x1").forGetter(zone -> zone.minX),
			Codec.INT.fieldOf("z1").forGetter(zone -> zone.minZ),
			Codec.INT.fieldOf("x2").forGetter(zone -> zone.maxX),
			Codec.INT.fieldOf("z2").forGetter(zone -> zone.maxZ)
	).apply(instance, RegionZone::new));

	public static ArgumentBuilder<CommandSourceStack, ?> createCommand(
			ArgumentBuilder<CommandSourceStack, ?> builder, ZoneManagementCommand.ZoneAdder addZone
	) {
		return builder.then(
				argument("pos1", ColumnPosArgument.columnPos()).then(
						argument("pos2", ColumnPosArgument.columnPos()).executes(ctx -> {
							return addZone.add(() -> new RegionZone(
									ColumnPosArgument.getColumnPos(ctx,"pos1"),
									ColumnPosArgument.getColumnPos(ctx,"pos2")
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
		return minX <= pos.getX() && maxX + 1 >= pos.getX() && minZ <= pos.getZ() && maxZ + 1 >= pos.getZ();
	}

	@Override
	public double getSize() {
		return ((double) maxX + 1 - minX) * ((double) maxZ + 1 - minZ) * getZoneRef().getWorld().getHeight();
	}

	@Override
	public InwardVector getInwardVector(Vec3 pos) {
		Vec3 northPivot = new Vec3(pos.x, pos.y, minZ);
		Vec3 southPivot = new Vec3(pos.x, pos.y, maxZ+1);
		Vec3 westPivot = new Vec3(minX, pos.y, pos.z);
		Vec3 eastPivot = new Vec3(maxX+1, pos.y, pos.z);
		return ZoneVectorHelper.getVectorForZoneFromPivots(
				this::contains, pos, northPivot, southPivot, westPivot, eastPivot
		);
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
