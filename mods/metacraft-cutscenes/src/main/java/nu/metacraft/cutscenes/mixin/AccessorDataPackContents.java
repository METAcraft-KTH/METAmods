package nu.metacraft.cutscenes.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;
import net.minecraft.commands.Commands;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.world.flag.FeatureFlagSet;

@Mixin(ReloadableServerResources.class)
public interface AccessorDataPackContents {

	@Invoker("<init>")
	static ReloadableServerResources init(
			LayeredRegistryAccess<RegistryLayer> dynamicRegistries,
			HolderLookup.Provider registries, FeatureFlagSet enabledFeatures,
			Commands.CommandSelection environment,
			List<Registry.PendingTags<?>> pendingTagLoads,
			int functionPermissionLevel
	) {
		throw new IllegalStateException("Mixin Error");
	}

}
