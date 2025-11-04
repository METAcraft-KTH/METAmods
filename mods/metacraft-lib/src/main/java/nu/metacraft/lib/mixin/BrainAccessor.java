package nu.metacraft.lib.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.Set;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.schedule.Activity;

@Mixin(Brain.class)
public interface BrainAccessor {
	@Accessor
	Map<Integer, Map<Activity, Set<BehaviorControl<?>>>> getAvailableBehaviorsByPriority();

}
