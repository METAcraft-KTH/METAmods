package se.datasektionen.mc.metacraft_lib.mixin;

import net.minecraft.loot.LootTableReporter;
import net.minecraft.loot.context.LootContextType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LootTableReporter.class)
public interface AccessorLootTableReporter {

	@Accessor
	LootContextType getContextType();

}
