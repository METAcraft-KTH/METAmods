package nu.metacraft.zones.zone.types;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.serialization.MapCodec;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import nu.metacraft.zones.ZoneManagementCommand;
import nu.metacraft.zones.zone.ZoneRegistry;

public class EmptyZone extends ZoneType {

	public static final EmptyZone INSTANCE = new EmptyZone();

	public static final MapCodec<EmptyZone> CODEC = MapCodec.unit(INSTANCE);

	public static ArgumentBuilder<CommandSourceStack, ?> createCommand(
			ArgumentBuilder<CommandSourceStack, ?> builder, ZoneManagementCommand.ZoneAdder addZone
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
