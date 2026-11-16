package se.metacraft.ovvar;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The {@code shaders} block of {@code config/ovvar.json}: where players with a shaderpack go to patch
 * it for ovvar, or download ovvar-client.jar (mods/ovvar-client). A file without the block (or the key) reads the default.
 *
 * @param patcherUrl the web page {@code /ovvar shaders} links to; blank means the default
 */
public record ShadersConfig(String patcherUrl) {
	public static final String DEFAULT_PATCHER_URL = "https://metacraft.se/ovvar/shaders";
	public static final ShadersConfig DEFAULT = new ShadersConfig(DEFAULT_PATCHER_URL);


	public static final MapCodec<ShadersConfig> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			ConfigFields.field(Codec.STRING, "patcher_url", DEFAULT.patcherUrl, "The page /ovvar shaders links to, where players patch their shaderpack for ovvar (or get", "ovvar-client.jar, which patches at startup or on a double-click). Blank means the default.").forGetter(ShadersConfig::patcherUrl)
	).apply(instance, ShadersConfig::new));

	/** The link {@code /ovvar shaders} sends: the configured page, or the default when it is blank. */
	public String patcherUrlOrDefault() {
		return patcherUrl == null || patcherUrl.isBlank() ? DEFAULT_PATCHER_URL : patcherUrl.strip();
	}
}
