package nu.metacraft.zones.zone.types;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.zones.ZoneManagementCommand;
import nu.metacraft.zones.zone.ZoneRegistry;

import java.util.function.BiFunction;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

import static net.minecraft.commands.Commands.argument;

public class CircleZone extends ZoneType {

	public static <T extends CircleZone> MapCodec<T> getCodec(BiFunction<BlockPos, Double, T> creator) {
		return RecordCodecBuilder.mapCodec(instance -> instance.group(
				BlockPos.CODEC.fieldOf("center").forGetter(zone -> zone.center),
				Codec.DOUBLE.fieldOf("radius").forGetter(zone -> zone.radius)
		).apply(instance, creator));
	}

	public static ArgumentBuilder<CommandSourceStack, ?> createCommand(
			ArgumentBuilder<CommandSourceStack, ?> builder, ZoneManagementCommand.ZoneAdder addZone,
			BiFunction<BlockPos, Double, ? extends CircleZone> creator
	) {
		return builder.then(
				argument("center", BlockPosArgument.blockPos()).then(
						argument("radius", DoubleArgumentType.doubleArg(0)).executes(ctx -> {
							return addZone.add(() -> creator.apply(
									BlockPosArgument.getBlockPos(ctx,"center"),
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
		return center.distToLowCornerSqr(pos.getX(), center.getY(), pos.getZ()) < Mth.square(radius);
	}

	@Override
	public double getSize() {
		return radius * radius * Math.PI * getZoneRef().getWorld().getHeight();
	}

	@Override
	public InwardVector getInwardVector(Vec3 pos) {
		var centerPos = Vec3.atCenterOf(center);
		return InwardVector.createFrom(new Vec3(centerPos.x, pos.y, centerPos.z), pos);
	}

	@Override
	public ZoneType copy() {
		return new CircleZone(center.immutable(), radius);
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
