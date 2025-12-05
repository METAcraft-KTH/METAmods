package nu.metacraft.zones.zone.types;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import nu.metacraft.zones.ZoneManagementCommand;
import nu.metacraft.zones.zone.ZoneRegistry;

public class DimensionLimiter extends ZoneType {

	public static final MapCodec<DimensionLimiter> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(limiter -> limiter.dimension)
	).apply(instance, DimensionLimiter::new));


	public static ArgumentBuilder<CommandSourceStack, ?> createCommand(
			ArgumentBuilder<CommandSourceStack, ?> builder, ZoneManagementCommand.ZoneAdder addZone
	) {
		return builder.then(
			Commands.argument("dimension", DimensionArgument.dimension()).executes(ctx -> {
				var dim = DimensionArgument.getDimension(ctx, "dimension");
				return addZone.add(() -> {
					return new DimensionLimiter(dim.dimension());
				}, ctx);
			})
		);
	}

	private final ResourceKey<Level> dimension;

	public DimensionLimiter(ResourceKey<Level> dimension) {
		this.dimension = dimension;
	}

	@Override
	public boolean contains(BlockPos pos) {
		return getZoneRef().getWorld().dimension().equals(dimension);
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
		return "DimensionLimiter[dimension=" + dimension.identifier() + "]";
	}
}
