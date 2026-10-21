package se.metacraft.kth_auth;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import nu.metacraft.lib.util.METACodecs;
import se.metacraft.config.container.ConfigContainer;

import java.net.URI;
import java.net.URL;
import java.util.Optional;

public record KTHAuthConfig(URL spamUrl, String spamApiToken, Optional<URL> webhookUrl) {
	public static final String PLACEHOLDER_SPAM_TOKEN = "ENTER SPAM TOKEN";

	public static final MapCodec<KTHAuthConfig> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		METACodecs.URL_CODEC.fieldOf("spam_url").forGetter(KTHAuthConfig::spamUrl),
		Codec.STRING.fieldOf("spam_api_token").forGetter(KTHAuthConfig::spamApiToken),
		METACodecs.URL_CODEC.optionalFieldOf("webhook_url").forGetter(KTHAuthConfig::webhookUrl)
	).apply(instance, KTHAuthConfig::new));

	private static final ConfigContainer<KTHAuthConfig> CONTAINER = ConfigContainer.Builder.create(CODEC, KTHAuthConfig::createDefault).build(KTHAuth.NAMESPACE);

	private static KTHAuthConfig createDefault() {
		try {
			return new KTHAuthConfig(
				URI.create("https://spam.datasektionen.se/api/legacy/sendmail").toURL(),
				PLACEHOLDER_SPAM_TOKEN,
				Optional.empty()
			);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static KTHAuthConfig getConfig() {
		return CONTAINER.get();
	}
}
