package nu.metacraft.cutscenes.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.clock.PackedClockStates;
import net.minecraft.world.clock.ServerClockManager;
import net.minecraft.world.clock.WorldClock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(ServerClockManager.class)
public interface ServerClockManagerAccessor {

	@Accessor
	Map<Holder<WorldClock>, ServerClockManager.ServerClockInstance> getClocks();

	@Accessor
	@Mutable
	void setPackedClockStates(PackedClockStates states);

}
