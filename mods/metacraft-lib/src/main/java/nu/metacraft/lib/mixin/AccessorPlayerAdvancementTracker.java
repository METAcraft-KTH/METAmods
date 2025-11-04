package nu.metacraft.lib.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;
import java.util.Set;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.server.PlayerAdvancements;

@Mixin(PlayerAdvancements.class)
public interface AccessorPlayerAdvancementTracker {

	@Accessor
	Map<AdvancementHolder, AdvancementProgress> getProgress();

	@Invoker
	void callStartProgress(AdvancementHolder advancement, AdvancementProgress progress);

	@Accessor
	Set<AdvancementHolder> getProgressChanged();

	@Invoker
	void callMarkForVisibilityUpdate(AdvancementHolder advancement);

}
