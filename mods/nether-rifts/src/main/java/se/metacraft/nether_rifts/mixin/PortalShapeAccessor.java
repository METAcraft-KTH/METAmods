package se.metacraft.nether_rifts.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.portal.PortalShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PortalShape.class)
public interface PortalShapeAccessor {
	@Accessor("axis")
	Direction.Axis netherRifts$axis();

	@Accessor("rightDir")
	Direction netherRifts$rightDir();

	@Accessor("bottomLeft")
	BlockPos netherRifts$bottomLeft();

	@Accessor("width")
	int netherRifts$width();

	@Accessor("height")
	int netherRifts$height();
}
