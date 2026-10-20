package metacraft.moredyes.mixin;

import metacraft.moredyes.banner.BannerPatterns;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.RegistryLoadTask;
import net.minecraft.world.level.block.entity.BannerPattern;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;
import java.util.stream.Stream;

/**
 * Where dynamic-registry entries actually get registered in 26.x: one {@code registerElements} call
 * per registry with every loaded entry. For the banner pattern registry we append one derived entry
 * per colour per loaded pattern, so datapack patterns get our colours too, with no build step.
 */
@Mixin(RegistryLoadTask.class)
public abstract class RegistryLoadTaskMixin<T> {
	@Shadow @Final protected RegistryDataLoader.RegistryData<T> data;

	@SuppressWarnings("unchecked")
	@ModifyVariable(method = "registerElements", at = @At("HEAD"), argsOnly = true)
	private Stream<RegistryLoadTask.PendingRegistration<T>> moredyes$deriveBannerPatterns(Stream<RegistryLoadTask.PendingRegistration<T>> stream) {
		if (!data.key().identifier().equals(Registries.BANNER_PATTERN.identifier())) return stream;
		List<RegistryLoadTask.PendingRegistration<BannerPattern>> loaded =
				(List<RegistryLoadTask.PendingRegistration<BannerPattern>>) (List<?>) stream.toList();
		return (Stream<RegistryLoadTask.PendingRegistration<T>>) (Stream<?>) BannerPatterns.derive(loaded).stream();
	}
}
