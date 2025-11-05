package nu.metacraft.resource_packs;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.util.UndashedUuid;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import net.minecraft.server.MinecraftServer;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.*;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ResourcePackServer implements AutoCloseable {

	private final MinecraftServer mc;
	private final ThreadPoolExecutor threadPool;
	private final HttpServer server;

	@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
	public ResourcePackServer(
			MinecraftServer mc, int port, Optional<String> localAddress,
			int maxConnections, Optional<SSLSettings> sslSettings
	) throws UnknownHostException, BindException {
		this.mc = mc;
		this.threadPool = new ThreadPoolExecutor(16, 24, 10, TimeUnit.SECONDS, new ArrayBlockingQueue<>(16));
		try {
			InetSocketAddress socketAddress;
			if (localAddress.isPresent()) {
				socketAddress = new InetSocketAddress(
						InetAddress.getByName(localAddress.get()), port
				);
			} else {
				socketAddress = new InetSocketAddress(port);
			}
			if (sslSettings.isPresent()) {
				var server = HttpsServer.create(socketAddress, maxConnections);
				fixHTTPs(server, sslSettings.get());
				this.server = server;
			} else {
				server = HttpServer.create(socketAddress, maxConnections);
			}
			server.setExecutor(this.threadPool);
			server.createContext("/", exchange -> {
				sendResponse(
						exchange.getRequestMethod().equals("GET"),
						exchange.getRequestURI().getPath().substring(1), exchange
				);
			});
		} catch (UnknownHostException | BindException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	//https://stackoverflow.com/questions/2308479/simple-java-https-server
	private void fixHTTPs(HttpsServer server, SSLSettings settings) throws NoSuchAlgorithmException, KeyStoreException, IOException, CertificateException, UnrecoverableKeyException, KeyManagementException {
		SSLContext sslContext = SSLContext.getInstance(settings.protocol);

		// Initialise the keystore
		char[] password = settings.keyStorePassword.toCharArray();
		KeyStore ks = KeyStore.getInstance(settings.keyStoreType);
		FileInputStream fis = new FileInputStream(settings.keyStorePath);
		ks.load(fis, password);

		// Set up the key manager factory
		KeyManagerFactory kmf = KeyManagerFactory.getInstance(settings.keyManagerAlgorithm);
		kmf.init(ks, password);

		// Set up the trust manager factory
		TrustManagerFactory tmf = TrustManagerFactory.getInstance(settings.trustManagerAlgorithm);
		tmf.init(ks);

		// Set up the HTTPS context and parameters
		sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

		server.setHttpsConfigurator(new HttpsConfigurator(sslContext));
	}

	public void start() {
		server.start();
	}

	@Override
	public void close() {
		server.stop(0);
	}

	protected boolean verify(Map<String, List<String>> headers) {
		final String agentKey = "User-Agent";
		if (!headers.containsKey(agentKey) || headers.get(agentKey).isEmpty()) return false;
		var agent = headers.get(agentKey).getFirst();
		if (!agent.startsWith("Minecraft Java")) {
			return false;
		}
		final String nameKey = "X-Minecraft-Username";
		if (!headers.containsKey(nameKey) || headers.get(nameKey).isEmpty()) return false;
		var name = headers.get(nameKey).getFirst();
		final String idKey = "X-Minecraft-UUID";
		if (!headers.containsKey(idKey) || headers.get(idKey).isEmpty()) return false;
		try {
			var id = UndashedUuid.fromString(headers.get(idKey).getFirst());
			return true;
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	private static void send403(HttpExchange exchange) throws IOException {
		exchange.sendResponseHeaders(403, -1);
	}

	protected void sendResponse(boolean success, String pack, HttpExchange exchange) throws IOException {
		boolean verified = verify(exchange.getRequestHeaders());
		if (!verified && !ResourcePackConfig.getConfig().allowManualDownloads()) {
			success = false;
		}
		UUID packID = null;
		try {
			packID = UUID.fromString(pack);
		} catch (IllegalArgumentException e) {
			success = false;
		}

		if (success) {

			byte[] data;

			var p = packID;
			try {
				var file = mc.submit(() -> {
					var packEntry = ResourcePackConfig.getConfig().getResourcePack(p);
					if (packEntry == null) {
						return null;
					}
					if (!verified && !packEntry.allowManualDownloads()) {
						return null;
					}
					return packEntry.getFile();
				}).get();
				if (file == null) {
					send403(exchange);
					return;
				}
				try (var stream = new BufferedInputStream(new FileInputStream(file.toFile()))) {
					data = stream.readAllBytes();
				}
			} catch (InterruptedException | ExecutionException e) {
				throw new RuntimeException(e);
			}
			//https://stackoverflow.com/questions/3413036/http-response-caching
			exchange.getResponseHeaders().add("Cache-Control", "no-cache, no-store, must-revalidate"); // HTTP 1.1.
			exchange.getResponseHeaders().add("Pragma", "no-cache"); // HTTP 1.0.
			exchange.getResponseHeaders().add("Expires", String.valueOf(0)); // Proxies.
			exchange.sendResponseHeaders(200, data.length);
			exchange.getResponseBody().write(data);
			exchange.getResponseBody().flush();
			exchange.getResponseBody().close();

		} else {
			send403(exchange);
		}
	}

	public record SSLSettings(
			String protocol, String keyStoreType, String keyStorePath, String keyStorePassword,
			String keyManagerAlgorithm, String trustManagerAlgorithm
	) {

		public static final Codec<SSLSettings> CODEC = RecordCodecBuilder.create(
				instance -> instance.group(
						Codec.STRING.optionalFieldOf("protocol", "TLS").forGetter(SSLSettings::protocol),
						Codec.STRING.optionalFieldOf("key_store_type", "JKS").forGetter(SSLSettings::keyStoreType),
						Codec.STRING.fieldOf("key_store_path").forGetter(SSLSettings::keyStorePath),
						Codec.STRING.fieldOf("key_store_password").forGetter(SSLSettings::keyStorePassword),
						Codec.STRING.optionalFieldOf("key_manager_algorithm", "SunX509").forGetter(SSLSettings::keyManagerAlgorithm),
						Codec.STRING.optionalFieldOf("key_trust_manager_algorithm", "SunX509").forGetter(SSLSettings::trustManagerAlgorithm)
				).apply(instance, SSLSettings::new)
		);

	}

}
