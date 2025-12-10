package nu.metacraft.core.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;

@Mixin(PathNavigation.class)
public interface PathNavigationAccessor {

	@Invoker
	Path callCreatePath(Set<BlockPos> positions, int range, boolean useHeadPos, int distance);

	@Accessor
	int getReachRange();

	@Accessor
	double getSpeedModifier();

}
