package se.datasektionen.mc.zones.zone.types;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.serialization.MapCodec;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.util.math.BlockPos;
import se.datasektionen.mc.zones.ZoneManagementCommand;
import se.datasektionen.mc.zones.zone.ZoneRegistry;

public class EmptyZone extends ZoneType {

	public static final EmptyZone INSTANCE = new EmptyZone();

	public static final MapCodec<EmptyZone> CODEC = MapCodec.unit(INSTANCE);

	public static ArgumentBuilder<ServerCommandSource, ?> createCommand(
			ArgumentBuilder<ServerCommandSource, ?> builder, ZoneManagementCommand.ZoneAdder addZone
	) {
		return builder.executes(ctx -> addZone.add(() -> INSTANCE, ctx));
	}

	private EmptyZone() {}

	@Override
	public boolean contains(BlockPos pos) {
		return false;
	}

	@Override
	public double getSize() {
		return 0;
	}

	@Override
	public ZoneType copy() {
		return this;
	}

	@Override
	public ZoneRegistry.ZoneTypeType<?> getType() {
		return ZoneRegistry.empty;
	}

	@Override
	public String toString() {
		return "Empty";
	}
}
