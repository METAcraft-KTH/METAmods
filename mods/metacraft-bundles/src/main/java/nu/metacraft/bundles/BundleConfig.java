package nu.metacraft.bundles;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.loader.api.FabricLoader;
import nu.metacraft.lib.config.container.ConfigContainer;

public class BundleConfig {

	private final boolean bundleRendering;

	public static final Codec<BundleConfig> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
					Codec.BOOL.fieldOf("enable_bundle_rendering").forGetter(BundleConfig::bundleRendering)
			).apply(instance, BundleConfig::new)
	);

	public BundleConfig(boolean bundleRendering) {
		this.bundleRendering = bundleRendering;
	}

	private static final ConfigContainer<BundleConfig> CONTAINER = ConfigContainer.Builder.create(
			CODEC, () -> new BundleConfig(true)
	).build(FabricLoader.getInstance().getConfigDir().resolve("metacraft-bundles.json"));

	public boolean bundleRendering() {
		return bundleRendering;
	}

	public static BundleConfig getInstance() {
		return CONTAINER.get();
	}

}
