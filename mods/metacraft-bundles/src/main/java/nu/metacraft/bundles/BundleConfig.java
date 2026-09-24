package nu.metacraft.bundles;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.loader.api.FabricLoader;
import nu.metacraft.lib.config.CommentCodec;
import nu.metacraft.lib.config.container.ConfigContainer;

public record BundleConfig(boolean bundleRendering) {

	public static final MapCodec<BundleConfig> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					CommentCodec.comment(
						Codec.BOOL.fieldOf("enable_bundle_rendering"),
						"Whether to use serverside bundle rendering, which is required for bundles to show the correct progressbar.",
						"When this is a enabled an additional resource pack will be sent to the client."
					).forGetter(BundleConfig::bundleRendering)
			).apply(instance, BundleConfig::new)
	);

	private static final ConfigContainer<BundleConfig> CONTAINER = ConfigContainer.Builder.create(
			CODEC, () -> new BundleConfig(true)
	).build(FabricLoader.getInstance().getConfigDir().resolve("metacraft-bundles.json"));


	public static BundleConfig getInstance() {
		return CONTAINER.get();
	}

}
