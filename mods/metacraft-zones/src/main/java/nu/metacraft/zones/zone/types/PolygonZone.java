package nu.metacraft.zones.zone.types;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.zones.ZoneManagementCommand;
import nu.metacraft.zones.zone.Zone;
import nu.metacraft.zones.zone.ZoneRegistry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.coordinates.ColumnPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ColumnPos;

import static net.minecraft.commands.Commands.argument;

public class PolygonZone extends ZoneType {

	private final List<ColumnPos> positions;

	private final List<TriangleZone> triangles;

	private final double area;

	public static final MapCodec<PolygonZone> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					TriangleZone.COLUMN_POS_CODEC.codec().listOf(
							3, Integer.MAX_VALUE
					).fieldOf("positions").forGetter(zone -> zone.positions)
			).apply(instance, PolygonZone::new)
	);

	private static final SimpleCommandExceptionType ODD = new SimpleCommandExceptionType(
			() -> "Argument count must be even"
	);

	private static final DynamicCommandExceptionType ERROR = new DynamicCommandExceptionType(
			error -> () -> ((Exception) error).getMessage()
	);

	public static ArgumentBuilder<CommandSourceStack, ?> createCommand(
			ArgumentBuilder<CommandSourceStack, ?> builder, ZoneManagementCommand.ZoneAdder addZone
	) {
		int max = 10;
		ArgumentBuilder<CommandSourceStack, ?> inside = argument("the_rest", StringArgumentType.greedyString()).executes(ctx -> {
			var args = getArgs(max, ctx);
			try {
				var elements = Arrays.stream(StringArgumentType.getString(ctx, "the_rest").split(" ")).mapToInt(
						Integer::parseInt
				).toArray();
				if (elements.length % 2 == 1) throw ODD.create();
				List<ColumnPos> positions = new ArrayList<>(elements.length/2);
				for (int i = 0; i < elements.length-1; i+=2) {
					var lhs = elements[i];
					var rhs = elements[i+1];
					positions.add(new ColumnPos(lhs, rhs));
				}
				List<ColumnPos> allPositions = Stream.concat(args, positions.stream()).toList();
				return addZone.add(() -> new PolygonZone(allPositions), ctx);
			} catch (NumberFormatException e) {
				throw ERROR.create(e);
			}
		});
		for (int i = max; i >= 3; i--) {
			var newOuterMost = argument("pos" + i, ColumnPosArgument.columnPos());
			int num = i;
			newOuterMost.executes(ctx -> {
				return addZone.add(() -> new PolygonZone(
						getArgs(num, ctx).toList()
				), ctx);
			});
			newOuterMost.then(inside);
			inside = newOuterMost;
		}
		return builder.then(
				argument("pos1", ColumnPosArgument.columnPos()).then(
						argument("pos2", ColumnPosArgument.columnPos()).then(inside)
				)
		);
	}

	private static Stream<ColumnPos> getArgs(int limit, CommandContext<CommandSourceStack> ctx) {
		return Stream.iterate(1, n -> n + 1).limit(limit).map(n -> "pos" + n).map(
				arg -> ColumnPosArgument.getColumnPos(ctx, arg)
		);
	}

	public PolygonZone(List<ColumnPos> positions) {
		this.positions = positions;
		this.triangles = new ArrayList<>(positions.size()-2);
		for (int i = 1; i < positions.size()-1; i++) {
			var triangle = new TriangleZone(
					positions.getFirst(),
					positions.get(i),
					positions.get(i+1)
			);
			triangle.setZoneRef(getZoneRef());
			this.triangles.add(triangle);
		}
		this.area = triangles.stream().mapToDouble(TriangleZone::getArea).sum();
	}

	@Override
	public boolean contains(BlockPos pos) {
		for (var t : triangles) {
			if (t.contains(pos)) return true;
		}
		return false;
	}

	@Override
	public void setZoneRef(Zone zone) {
		super.setZoneRef(zone);
		this.triangles.forEach(triangle -> triangle.setZoneRef(zone));
	}

	@Override
	public double getSize() {
		return area * getZoneRef().getWorld().getHeight();
	}

	@Override
	public InwardVector getInwardVector(Vec3 pos) {
		double centerX = positions.stream().mapToDouble(ColumnPos::x).sum() / positions.size();
		double centerZ = positions.stream().mapToDouble(ColumnPos::z).sum() / positions.size();
		return InwardVector.createFrom(new Vec3(centerX, pos.y, centerZ), pos);
	}

	@Override
	public ZoneType copy() {
		return new PolygonZone(positions);
	}

	@Override
	public ZoneRegistry.ZoneTypeType<?> getType() {
		return ZoneRegistry.polygon;
	}

	@Override
	public String toString() {
		return "Polygon " + positions;
	}
}
