package se.datasektionen.mc.metacraft_lib.mixin;

import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.sensor.Sensor;
import net.minecraft.entity.ai.brain.sensor.SensorType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Collection;

@Mixin(Brain.Profile.class)
public interface AccessorBrainProfile {

	@Accessor
	Collection<? extends MemoryModuleType<?>> getMemoryModules();
	@Accessor
	Collection<? extends SensorType<? extends Sensor<?>>> getSensors();

}
