package se.metacraft.kth_auth;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

public class MailSender {
	private record MailBody(String from, String to, String subject, String html, String key) {
		private static final Codec<MailBody> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("from").forGetter(MailBody::from),
			Codec.STRING.fieldOf("to").forGetter(MailBody::to),
			Codec.STRING.fieldOf("subject").forGetter(MailBody::subject),
			Codec.STRING.fieldOf("html").forGetter(MailBody::html),
			Codec.STRING.fieldOf("key").forGetter(MailBody::key)
		).apply(instance, MailBody::new));
	}

	private static final int TIMEOUT_MS = 10000; // 10 seconds

	private final KTHAuthConfig config;

	public MailSender(KTHAuthConfig config) {
		this.config = config;
	}

	/**
	 * Send an email.
	 *
	 * @param to The recipient email address.
	 * @param subject The email subject.
	 * @param html The email content as HTML.
	 * @return A future that completes or fails.
	 */
	public CompletableFuture<Void> sendMail(String to, String subject, String html) {
		return CompletableFuture.runAsync(() -> {
			try {
				this.performSendMail(to, subject, html);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		});
	}

	private void performSendMail(String to, String subject, String html) throws IOException {
		if (this.config.spamApiToken().equals(KTHAuthConfig.PLACEHOLDER_SPAM_TOKEN)) {
			KTHAuth.LOGGER.warn("No Spam API token provided, email will not be sent.");
			KTHAuth.LOGGER.info("To: {}", to);
			KTHAuth.LOGGER.info("Subject: {}", subject);
			KTHAuth.LOGGER.info("{}", html);
			return;
		}
		HttpURLConnection conn = (HttpURLConnection) this.config.spamUrl().toURL().openConnection();
		conn.setRequestMethod("POST");
		conn.setRequestProperty("Content-Type", "application/json");
		conn.setRequestProperty("User-Agent", KTHAuth.USER_AGENT);
		conn.setRequestProperty("Authorization", "Bearer " + this.config.spamApiToken());
		conn.setConnectTimeout(TIMEOUT_MS);
		conn.setReadTimeout(TIMEOUT_MS);

		MailBody body = new MailBody("no-reply@datasektionen.se", to, subject, html, this.config.spamApiToken());
		String json = MailBody.CODEC.encodeStart(JsonOps.INSTANCE, body)
			.getOrThrow(error -> new IOException("Failed to encode mail body: " + error))
			.toString();

		byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
		conn.setDoOutput(true);
		conn.getOutputStream().write(bytes);
		conn.getOutputStream().close();

		if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
			throw new IOException("HTTP " + conn.getResponseCode());
		}
	}
}
