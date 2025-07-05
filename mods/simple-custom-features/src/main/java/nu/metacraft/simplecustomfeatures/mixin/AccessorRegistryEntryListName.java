package nu.metacraft.simplecustomfeatures.mixin;

import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(RegistryEntryList.Named.class)
public interface AccessorRegistryEntryListName<T> {

	@Accessor
	void setEntries(List<RegistryEntry<T>> entries);

}
