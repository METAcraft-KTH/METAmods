package se.datasektionen.mc.zones.zone.types;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import se.datasektionen.mc.zones.ZoneManagementCommand;
import se.datasektionen.mc.zones.zone.ZoneRegistry;

import static net.minecraft.server.command.CommandManager.argument;

public class BoxZone extends ZoneType {

	public static final MapCodec<BoxZone> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			BlockBox.CODEC.fieldOf("box").forGetter(zone -> zone.box)
	).apply(instance, BoxZone::new));

	public static ArgumentBuilder<ServerCommandSource, ?> createCommand(
			ArgumentBuilder<ServerCommandSource, ?> builder, ZoneManagementCommand.ZoneAdder addZone
	) {
		return builder.then(
			argument("pos1", BlockPosArgumentType.blockPos()).then(
				argument("pos2", BlockPosArgumentType.blockPos()).executes(ctx -> {
					BlockPos pos1 = BlockPosArgumentType.getBlockPos(ctx,"pos1");
					BlockPos pos2 = BlockPosArgumentType.getBlockPos(ctx,"pos2");
					BlockBox box = new BlockBox(
							pos1.getX(), pos1.getY(), pos1.getZ(), pos2.getX(), pos2.getY(), pos2.getZ()
					);
					return addZone.add(() -> new BoxZone(box), ctx);
				})
			)
		);
	}

	protected final BlockBox box;

	public BoxZone(BlockBox box) {
		this.box = box;
	}

	@Override
	public boolean contains(BlockPos pos) {
		return box.contains(pos);
	}

	@Override
	public double getSize() {
		return (double) box.getBlockCountX() * box.getBlockCountY() * box.getBlockCountZ();
	}

	@Override
	public ZoneType copy() {
		return new BoxZone(new BlockBox(
				box.getMinX(), box.getMinY(), box.getMinZ(),
				box.getMaxX(), box.getMaxY(), box.getMaxZ()
		));
	}

	@Override
	public ZoneRegistry.ZoneTypeType<? extends BoxZone> getType() {
		return ZoneRegistry.box;
	}

	@Override
	public String toString() {
		return "Box[" +
				"from:{x=" + box.getMinX() + ", y=" + box.getMinY() + ", z=" + box.getMinZ() + "}, " +
				"to:{x=" + box.getMaxX() + ", y=" + box.getMaxY() + ", z=" + box.getMaxZ() + "}"  +
		"]";
	}
}
