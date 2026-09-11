package nu.metacraft.lib.util.helper;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import nu.metacraft.lib.mixin.RegistryDataLoaderRegistryInfoLookupAccessor;
import nu.metacraft.lib.mixin.RegistryOpsAccessor;
import org.jspecify.annotations.NonNull;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public class LookupHelper {

	public static Optional<HolderLookup.Provider> getProvider(RegistryOps<?> registryOps) {
		if (registryOps instanceof RegistryOpsAccessor r) {
			var getter = r.getLookupProvider();
			if (getter instanceof RegistryDataLoaderRegistryInfoLookupAccessor g) {
				return Optional.of(new Lookup(g.getRegistries()));
			} else if (getter instanceof RegistryOpsAccessor.HolderLookupAdapter g) {
				return Optional.of(g.getLookupProvider());
			}
		}
		return Optional.empty();
	}

	public record Lookup(
			Map<ResourceKey<? extends Registry<?>>, HolderGetter<?>> registries
	) implements HolderLookup.Provider {

		@Override
		public @NonNull Stream<ResourceKey<? extends Registry<?>>> listRegistryKeys() {
			return registries.keySet().stream();
		}

		@Override
		public <T> @NonNull Optional<? extends HolderLookup.RegistryLookup<T>> lookup(
				@NonNull ResourceKey<? extends Registry<? extends T>> key
		) {
			//noinspection unchecked
			return Optional.ofNullable((HolderLookup.RegistryLookup<T>) registries.get(key));
		}
	}

}
