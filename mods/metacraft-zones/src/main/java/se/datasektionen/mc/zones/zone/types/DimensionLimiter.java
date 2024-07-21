package se.datasektionen.mc.zones.zone.types;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.command.argument.DimensionArgumentType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import se.datasektionen.mc.zones.ZoneManagementCommand;
import se.datasektionen.mc.zones.zone.ZoneRegistry;

public class DimensionLimiter extends ZoneType {

	public static final MapCodec<DimensionLimiter> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		World.CODEC.fieldOf("dimension").forGetter(limiter -> limiter.dimension)
	).apply(instance, DimensionLimiter::new));


	public static ArgumentBuilder<ServerCommandSource, ?> createCommand(
			ArgumentBuilder<ServerCommandSource, ?> builder, ZoneManagementCommand.ZoneAdder addZone
	) {
		return builder.then(
			CommandManager.argument("dimension", DimensionArgumentType.dimension()).executes(ctx -> {
				var dim = DimensionArgumentType.getDimensionArgument(ctx, "dimension");
				return addZone.add(() -> {
					return new DimensionLimiter(dim.getRegistryKey());
				}, ctx);
			})
		);
	}

	private final RegistryKey<World> dimension;

	public DimensionLimiter(RegistryKey<World> dimension) {
		this.dimension = dimension;
	}

	@Override
	public boolean contains(BlockPos pos) {
		return getZoneRef().getWorld().getRegistryKey().equals(dimension);
	}

	@Override
	public double getSize() {
		return Math.pow(getZoneRef().getWorld().getWorldBorder().getSize() * 2, 2) * getZoneRef().getWorld().getHeight();
	}

	@Override
	public ZoneType copy() {
		return new DimensionLimiter(dimension);
	}

	@Override
	public ZoneRegistry.ZoneTypeType<?> getType() {
		return ZoneRegistry.dimension;
	}

	@Override
	public String toString() {
		return "DimensionLimiter[dimension=" + dimension.getValue() + "]";
	}
}
