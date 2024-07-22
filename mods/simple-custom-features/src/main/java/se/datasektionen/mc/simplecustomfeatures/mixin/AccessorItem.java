package se.datasektionen.mc.simplecustomfeatures.mixin;

import net.minecraft.item.Item;
import net.minecraft.registry.entry.RegistryEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Item.class)
public interface AccessorItem {

	@Accessor
	@Mutable
	void setRegistryEntry(RegistryEntry.Reference<Item> registryEntry);

}
