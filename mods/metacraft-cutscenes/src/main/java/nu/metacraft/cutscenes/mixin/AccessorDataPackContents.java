package nu.metacraft.cutscenes.mixin;

import net.minecraft.registry.CombinedDynamicRegistries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.ServerDynamicRegistryType;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.server.DataPackContents;
import net.minecraft.server.command.CommandManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(DataPackContents.class)
public interface AccessorDataPackContents {

	@Invoker("<init>")
	static DataPackContents init(
			CombinedDynamicRegistries<ServerDynamicRegistryType> dynamicRegistries,
			RegistryWrapper.WrapperLookup registries, FeatureSet enabledFeatures,
			CommandManager.RegistrationEnvironment environment,
			List<Registry.PendingTagLoad<?>> pendingTagLoads,
			int functionPermissionLevel
	) {
		throw new IllegalStateException("Mixin Error");
	}

}
