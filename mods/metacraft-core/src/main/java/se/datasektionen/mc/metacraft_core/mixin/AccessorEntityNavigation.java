package se.datasektionen.mc.metacraft_core.mixin;

import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Set;

@Mixin(EntityNavigation.class)
public interface AccessorEntityNavigation {

	@Invoker
	Path callFindPathTo(Set<BlockPos> positions, int range, boolean useHeadPos, int distance);

	@Accessor
	int getCurrentDistance();

	@Accessor
	double getSpeed();

}
