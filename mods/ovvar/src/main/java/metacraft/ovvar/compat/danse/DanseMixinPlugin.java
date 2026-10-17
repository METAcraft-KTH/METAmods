package metacraft.ovvar.compat.danse;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Keeps {@code ovvar-danse.mixins.json} out of the way on a server without Danse. Every mixin in
 * that config targets a Danse class, and applying one with the target absent is a hard error, so
 * the whole config is switched off unless Danse is loaded — and off as well when the compat layer
 * is disabled with {@code -Dovvar.danse.compat=false}, which is how the "before" screenshot in the
 * client test is taken from the very same build.
 */
public final class DanseMixinPlugin implements IMixinConfigPlugin {
	private boolean apply;

	@Override
	public void onLoad(String mixinPackage) {
		// FabricLoader is up well before mixin configs are read; DanseHooks is not touched here
		// because loading it would pull in classes that name Danse types.
		this.apply = !"false".equalsIgnoreCase(System.getProperty(DanseHooks.PROPERTY, "true"))
				&& FabricLoader.getInstance().isModLoaded("danse");
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		return apply;
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

	@Override
	public List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
