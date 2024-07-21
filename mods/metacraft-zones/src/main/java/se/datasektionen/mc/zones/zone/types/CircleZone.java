package se.datasektionen.mc.zones.zone.types;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import se.datasektionen.mc.zones.ZoneManagementCommand;
import se.datasektionen.mc.zones.zone.ZoneRegistry;

import java.util.function.BiFunction;

import static net.minecraft.server.command.CommandManager.argument;

public class CircleZone extends ZoneType {

	public static <T extends CircleZone> MapCodec<T> getCodec(BiFunction<BlockPos, Double, T> creator) {
		return RecordCodecBuilder.mapCodec(instance -> instance.group(
				BlockPos.CODEC.fieldOf("center").forGetter(zone -> zone.center),
				Codec.DOUBLE.fieldOf("radius").forGetter(zone -> zone.radius)
		).apply(instance, creator));
	}

	public static ArgumentBuilder<ServerCommandSource, ?> createCommand(
			ArgumentBuilder<ServerCommandSource, ?> builder, ZoneManagementCommand.ZoneAdder addZone,
			BiFunction<BlockPos, Double, ? extends CircleZone> creator
	) {
		return builder.then(
				argument("center", BlockPosArgumentType.blockPos()).then(
						argument("radius", DoubleArgumentType.doubleArg(0)).executes(ctx -> {
							return addZone.add(() -> creator.apply(
									BlockPosArgumentType.getBlockPos(ctx,"center"),
									DoubleArgumentType.getDouble(ctx,"radius")
							), ctx);
						})
				)
		);
	}

	protected BlockPos center;
	protected double radius;

	public CircleZone(BlockPos center, double radius) {
		this.center = center;
		this.radius = radius;
	}

	@Override
	public boolean contains(BlockPos pos) {
		return center.getSquaredDistance(pos.getX(), center.getY(), pos.getZ()) < MathHelper.square(radius);
	}

	@Override
	public double getSize() {
		return radius * radius * Math.PI * getZoneRef().getWorld().getHeight();
	}

	@Override
	public ZoneType copy() {
		return new CircleZone(center.toImmutable(), radius);
	}

	@Override
	public ZoneRegistry.ZoneTypeType<? extends CircleZone> getType() {
		return ZoneRegistry.circle;
	}

	@Override
	public String toString() {
		return "Circle[" +
				"center:{x=" + center.getX() + ", y=" + center.getY() + ", z=" + center.getZ() + "}, " +
				"radius=" + radius +
		"]";
	}
}
