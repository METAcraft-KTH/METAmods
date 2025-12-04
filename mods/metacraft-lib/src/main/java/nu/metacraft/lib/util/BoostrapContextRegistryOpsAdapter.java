package nu.metacraft.lib.util;

import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.Registry;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class BoostrapContextRegistryOpsAdapter implements RegistryOps.RegistryInfoLookup {

	public static <T> RegistryOps<T> createRegistryOps(DynamicOps<T> ops, BootstrapContext<?> ctx) {
		return RegistryOps.create(ops, new BoostrapContextRegistryOpsAdapter(ctx));
	}

	private final BootstrapContext<?> ctx;
	private final Map<ResourceKey<? extends Registry<?>>, Optional<? extends RegistryOps.RegistryInfo<?>>> lookups = new ConcurrentHashMap<>();

	public BoostrapContextRegistryOpsAdapter(BootstrapContext<?> ctx) {
		this.ctx = ctx;
	}

	private static final HolderOwner<?> DUMMY_OWNER = new HolderOwner<>() {};

	@SuppressWarnings("unchecked")
	private static <T> HolderOwner<T> getDummyOwner() {
		return (HolderOwner<T>) DUMMY_OWNER;
	}

	private Optional<RegistryOps.RegistryInfo<Object>> createLookup(ResourceKey<? extends Registry<?>> registryKey) {
		return Optional.of(new RegistryOps.RegistryInfo<>(getDummyOwner(), this.ctx.lookup(registryKey), Lifecycle.stable()));
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> @NotNull Optional<RegistryOps.RegistryInfo<T>> lookup(ResourceKey<? extends Registry<? extends T>> registryKey) {
		return (Optional<RegistryOps.RegistryInfo<T>>) this.lookups.computeIfAbsent(registryKey, this::createLookup);
	}
}
