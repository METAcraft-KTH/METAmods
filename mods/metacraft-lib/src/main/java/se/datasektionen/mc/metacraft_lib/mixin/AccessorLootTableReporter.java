package se.datasektionen.mc.metacraft_lib.mixin;

import net.minecraft.loot.LootTableReporter;
import net.minecraft.util.context.ContextType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LootTableReporter.class)
public interface AccessorLootTableReporter {

	@Accessor
	ContextType getContextType();

}
