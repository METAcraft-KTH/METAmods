package se.metacraft.kth_auth;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;
import se.metacraft.config.container.ConfigContainer;
import se.metacraft.config.util.CommentCodec;

import java.net.URI;
import java.util.Optional;

public record KTHAuthConfig(URI spamUrl, String spamApiToken, Optional<URI> webhookUrl) {
	public static final String PLACEHOLDER_SPAM_TOKEN = "ENTER SPAM TOKEN";

	public static final MapCodec<KTHAuthConfig> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		CommentCodec.comment(ExtraCodecs.UNTRUSTED_URI.fieldOf("spam_url"),
			"URL to the sendmail endpoint at the Datasektionen system Spam."
		).forGetter(KTHAuthConfig::spamUrl),
		CommentCodec.comment(Codec.STRING.fieldOf("spam_api_token"),
			"API token for Spam. Ask d-sys for such a token. Keep this token secret. Contact d-sys in case a token is leaked or needs to rotate."
		).forGetter(KTHAuthConfig::spamApiToken),
		CommentCodec.comment(ExtraCodecs.UNTRUSTED_URI,
			"Optional URL to a Discord webhook. If set, the mod will send a message to this webhook logging verifications."
		).optionalFieldOf("webhook_url").forGetter(KTHAuthConfig::webhookUrl)
	).apply(instance, KTHAuthConfig::new));

	private static final ConfigContainer<KTHAuthConfig> CONTAINER = ConfigContainer.Builder.create(CODEC, KTHAuthConfig::createDefault).disableConfigScreen().build(KTHAuth.NAMESPACE);

	private static KTHAuthConfig createDefault() {
		try {
			return new KTHAuthConfig(
				URI.create("https://spam.datasektionen.se/api/legacy/sendmail"),
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
