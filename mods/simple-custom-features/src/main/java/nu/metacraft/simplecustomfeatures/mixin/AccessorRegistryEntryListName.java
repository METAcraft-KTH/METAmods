package nu.metacraft.simplecustomfeatures.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;

@Mixin(HolderSet.Named.class)
public interface AccessorRegistryEntryListName<T> {

	@Accessor
	void setContents(List<Holder<T>> entries);

}
