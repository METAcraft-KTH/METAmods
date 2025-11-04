package nu.metacraft.lib.mixin;

import net.minecraft.util.context.ContextKeySet;
import net.minecraft.world.level.storage.loot.ValidationContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ValidationContext.class)
public interface ValidationContextAccessor {

	@Accessor
	ContextKeySet getContextKeySet();

}
