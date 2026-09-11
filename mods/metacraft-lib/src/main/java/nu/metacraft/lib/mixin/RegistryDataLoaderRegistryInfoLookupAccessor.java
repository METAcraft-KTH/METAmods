package nu.metacraft.lib.mixin;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(targets = "net.minecraft.resources.RegistryDataLoader$3")
public interface RegistryDataLoaderRegistryInfoLookupAccessor {

	@Accessor("val$result")
	Map<ResourceKey<? extends Registry<?>>, HolderGetter<?>> getRegistries();

}
