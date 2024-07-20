package se.datasektionen.mc.metacraft_lib.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.Activity;
import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.entity.ai.brain.task.Task;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.Set;

@Mixin(Brain.class)
public interface AccessorBrain {
	@Accessor
	Map<Integer, Map<Activity, Set<Task<?>>>> getTasks();

}
