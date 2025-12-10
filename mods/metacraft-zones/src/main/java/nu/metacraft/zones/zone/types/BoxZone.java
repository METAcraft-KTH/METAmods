package nu.metacraft.zones.zone.types;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import nu.metacraft.zones.ZoneManagementCommand;
import nu.metacraft.zones.zone.ZoneRegistry;

import static net.minecraft.commands.Commands.argument;

public class BoxZone extends ZoneType {

	public static final MapCodec<BoxZone> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			BoundingBox.CODEC.fieldOf("box").forGetter(zone -> zone.box)
	).apply(instance, BoxZone::new));

	public static ArgumentBuilder<CommandSourceStack, ?> createCommand(
			ArgumentBuilder<CommandSourceStack, ?> builder, ZoneManagementCommand.ZoneAdder addZone
	) {
		return builder.then(
			argument("pos1", BlockPosArgument.blockPos()).then(
				argument("pos2", BlockPosArgument.blockPos()).executes(ctx -> {
					BlockPos pos1 = BlockPosArgument.getBlockPos(ctx,"pos1");
					BlockPos pos2 = BlockPosArgument.getBlockPos(ctx,"pos2");
					BoundingBox box = new BoundingBox(
							pos1.getX(), pos1.getY(), pos1.getZ(), pos2.getX(), pos2.getY(), pos2.getZ()
					);
					return addZone.add(() -> new BoxZone(box), ctx);
				})
			)
		);
	}

	protected final BoundingBox box;

	public BoxZone(BoundingBox box) {
		this.box = box;
	}

	@Override
	public boolean contains(BlockPos pos) {
		return box.isInside(pos);
	}

	@Override
	public double getSize() {
		return (double) box.getXSpan() * box.getYSpan() * box.getZSpan();
	}

	@Override
	public ZoneType copy() {
		return new BoxZone(new BoundingBox(
				box.minX(), box.minY(), box.minZ(),
				box.maxX(), box.maxY(), box.maxZ()
		));
	}

	@Override
	public ZoneRegistry.ZoneTypeType<? extends BoxZone> getType() {
		return ZoneRegistry.box;
	}

	@Override
	public String toString() {
		return "Box[" +
				"from:{x=" + box.minX() + ", y=" + box.minY() + ", z=" + box.minZ() + "}, " +
				"to:{x=" + box.maxX() + ", y=" + box.maxY() + ", z=" + box.maxZ() + "}"  +
		"]";
	}
}
