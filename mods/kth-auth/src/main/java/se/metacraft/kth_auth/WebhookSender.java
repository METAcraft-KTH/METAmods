package se.metacraft.kth_auth;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/**
 * Sends Discord webhook messages.
 */
public class WebhookSender {
	private record WebhookBody(String username, String content) {
		private static final Codec<WebhookBody> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("username").forGetter(WebhookBody::username),
			Codec.STRING.fieldOf("content").forGetter(WebhookBody::content)
		).apply(instance, WebhookBody::new));
	}

	private static final int TIMEOUT_MS = 10000; // 10 seconds

	private final KTHAuthConfig config;

	public WebhookSender(KTHAuthConfig config) {
		this.config = config;
	}

	/**
	 * Send a simple message to the webhook endpoint.
	 *
	 * @param content The message content.
	 */
	public void sendMessage(String content) {
		CompletableFuture.runAsync(() -> {
			try {
				this.performSendMessage(content);
			} catch (IOException e) {
				KTHAuth.LOGGER.error("Failed to send webhook message: {}", e.getMessage());
			}
		});
	}

	private void performSendMessage(String content) throws IOException {
		if (this.config.webhookUrl().isEmpty()) {
			KTHAuth.LOGGER.info("Webhook URL is not configured, skipping sending message.");
			return;
		}
		HttpURLConnection conn = (HttpURLConnection) this.config.webhookUrl().get().toURL().openConnection();
		conn.setRequestMethod("POST");
		conn.setRequestProperty("Content-Type", "application/json");
		conn.setRequestProperty("User-Agent", KTHAuth.USER_AGENT);
		conn.setConnectTimeout(TIMEOUT_MS);
		conn.setReadTimeout(TIMEOUT_MS);

		WebhookBody body = new WebhookBody("KTH-Auth", content);
		String json = WebhookBody.CODEC.encodeStart(JsonOps.INSTANCE, body)
			.getOrThrow(error -> new IOException("Failed to encode webhook body: " + error))
			.toString();

		byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
		conn.setDoOutput(true);
		conn.getOutputStream().write(bytes);
		conn.getOutputStream().close();

		if (conn.getResponseCode() < 200 || conn.getResponseCode() >= 300) {
			throw new IOException("HTTP " + conn.getResponseCode());
		}
	}
}
