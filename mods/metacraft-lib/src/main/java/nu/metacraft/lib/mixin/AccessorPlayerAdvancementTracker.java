package nu.metacraft.lib.mixin;

import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.advancement.PlayerAdvancementTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;
import java.util.Set;

@Mixin(PlayerAdvancementTracker.class)
public interface AccessorPlayerAdvancementTracker {

	@Accessor
	Map<AdvancementEntry, AdvancementProgress> getProgress();

	@Invoker
	void callInitProgress(AdvancementEntry advancement, AdvancementProgress progress);

	@Accessor
	Set<AdvancementEntry> getProgressUpdates();

	@Invoker
	void callOnStatusUpdate(AdvancementEntry advancement);

}
