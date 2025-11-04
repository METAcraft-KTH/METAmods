package nu.metacraft.lib.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Collection;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;

@Mixin(Brain.Provider.class)
public interface BrainProviderAccessor {

	@Accessor
	Collection<? extends MemoryModuleType<?>> getMemoryTypes();
	@Accessor
	Collection<? extends SensorType<? extends Sensor<?>>> getSensorTypes();

}
