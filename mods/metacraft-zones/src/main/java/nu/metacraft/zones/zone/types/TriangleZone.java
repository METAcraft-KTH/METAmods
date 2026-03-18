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
import nu.metacraft.zones.zone.ZoneRegistry;

import static net.minecraft.commands.Commands.argument;

public class TriangleZone extends ZoneType {

	private final ColumnPos pos1;
	private final ColumnPos pos2;
	private final ColumnPos pos3;

	private final double area;
	public static final MapCodec<ColumnPos> COLUMN_POS_CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Codec.INT.fieldOf("x").forGetter(ColumnPos::x),
					Codec.INT.fieldOf("z").forGetter(ColumnPos::z)
			).apply(instance, ColumnPos::new)
	);

	public static final MapCodec<TriangleZone> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					COLUMN_POS_CODEC.fieldOf("pos1").forGetter(zone -> zone.pos1),
					COLUMN_POS_CODEC.fieldOf("pos2").forGetter(zone -> zone.pos2),
					COLUMN_POS_CODEC.fieldOf("pos3").forGetter(zone -> zone.pos3)
			).apply(instance, TriangleZone::new)
	);

	public static ArgumentBuilder<CommandSourceStack, ?> createCommand(
			ArgumentBuilder<CommandSourceStack, ?> builder, ZoneManagementCommand.ZoneAdder addZone
	) {
		return builder.then(
			argument("pos1", ColumnPosArgument.columnPos()).then(
				argument("pos2", ColumnPosArgument.columnPos()).then(
					argument("pos3", ColumnPosArgument.columnPos()).executes(ctx -> {
						return addZone.add(() -> new TriangleZone(
								ColumnPosArgument.getColumnPos(ctx,"pos1"),
								ColumnPosArgument.getColumnPos(ctx,"pos2"),
								ColumnPosArgument.getColumnPos(ctx,"pos3")
						), ctx);
					})
				)
			)
		);
	}

	public TriangleZone(ColumnPos pos1, ColumnPos pos2, ColumnPos pos3) {
		this.pos1 = pos1;
		this.pos2 = pos2;
		this.pos3 = pos3;
		this.area = getArea(pos1, pos2, pos3);
	}

	@Override
	public boolean contains(BlockPos pos) {
		var barPos1 = 0.5 * getArea(pos, pos2, pos3) / area;
		var barPos2 = 0.5 * getArea(pos, pos3, pos1) / area;
		var barPos3 = 0.5 * getArea(pos, pos1, pos2) / area;
		return barPos1 >= 0 && barPos2 >= 0 && barPos3 >= 0;
	}

	private static double getArea(ColumnPos pos1, ColumnPos pos2, ColumnPos pos3) {
		return getArea(pos1.x(), pos1.z(), pos2.x(), pos2.z(), pos3.x(), pos3.z());
	}

	private static double getArea(BlockPos pos, ColumnPos pos1, ColumnPos pos2) {
		return getArea(pos.getX(), pos.getZ(), pos1.x(), pos1.z(), pos2.x(), pos2.z());
	}

	private static double getArea(int x1, int z1, int x2, int z2, int x3, int z3) {
		return (double) x1 * (z2 - z3) + (double) x2 * (z3 - z1) + (double) x3 * (z1 - z2);
	}

	public double getArea() {
		return getArea(pos1, pos2, pos3);
	}

	@Override
	public double getSize() {
		return getArea() * getZoneRef().getWorld().getHeight();
	}

	@Override
	public InwardVector getInwardVector(Vec3 pos) {
		double centerX = (double) pos1.x() + pos2.x() + pos3.x() / 3.0;
		double centerZ = (double) pos1.z() + pos2.z() + pos3.z() / 3.0;
		return InwardVector.createFrom(new Vec3(centerX, pos.y, centerZ), pos);
	}

	@Override
	public ZoneType copy() {
		return new TriangleZone(pos1, pos2, pos3);
	}

	@Override
	public ZoneRegistry.ZoneTypeType<?> getType() {
		return ZoneRegistry.triangle;
	}

	@Override
	public String toString() {
		return "Triangle[pos1=" + pos1 + ", pos2=" + pos2 + ", pos3=" + pos3 + "]";
	}
}
