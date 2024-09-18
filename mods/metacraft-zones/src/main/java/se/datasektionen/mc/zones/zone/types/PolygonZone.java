package se.datasektionen.mc.zones.zone.types;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.command.argument.ColumnPosArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ColumnPos;
import se.datasektionen.mc.zones.ZoneManagementCommand;
import se.datasektionen.mc.zones.zone.Zone;
import se.datasektionen.mc.zones.zone.ZoneRegistry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static net.minecraft.server.command.CommandManager.argument;

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

	public static ArgumentBuilder<ServerCommandSource, ?> createCommand(
			ArgumentBuilder<ServerCommandSource, ?> builder, ZoneManagementCommand.ZoneAdder addZone
	) {
		int max = 10;
		ArgumentBuilder<ServerCommandSource, ?> inside = argument("the_rest", StringArgumentType.greedyString()).executes(ctx -> {
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
			var newOuterMost = argument("pos" + i, ColumnPosArgumentType.columnPos());
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
				argument("pos1", ColumnPosArgumentType.columnPos()).then(
						argument("pos2", ColumnPosArgumentType.columnPos()).then(inside)
				)
		);
	}

	private static Stream<ColumnPos> getArgs(int limit, CommandContext<ServerCommandSource> ctx) {
		return Stream.iterate(1, n -> n + 1).limit(limit).map(n -> "pos" + n).map(
				arg -> ColumnPosArgumentType.getColumnPos(ctx, arg)
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
