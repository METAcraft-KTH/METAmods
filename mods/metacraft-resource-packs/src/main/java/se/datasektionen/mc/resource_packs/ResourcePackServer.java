package se.datasektionen.mc.resource_packs;

import com.mojang.util.UndashedUuid;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.server.MinecraftServer;

import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

public class ResourcePackServer implements AutoCloseable {

	private final MinecraftServer mc;
	private final HttpServer server;

	public ResourcePackServer(MinecraftServer mc, int port, String localAddress, int maxConnections) {
		this.mc = mc;
		try {
			server = HttpServer.create(new InetSocketAddress(
					InetAddress.getByName(localAddress), port
			), maxConnections);
			server.createContext("/", exchange -> {
				sendResponse(
						exchange.getRequestMethod().equals("GET") && verify(exchange.getRequestHeaders()),
						exchange.getRequestURI().getPath().substring(1), exchange
				);
			});
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
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

	protected void sendResponse(boolean success, String pack, HttpExchange exchange) throws IOException {
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
					return ResourcePackConfig.getConfig().getResourcePack(p).getFile();
				}).get();
				try (var stream = new BufferedInputStream(new FileInputStream(file.toFile()))) {
					data = stream.readAllBytes();
				}
			} catch (InterruptedException | ExecutionException e) {
				throw new RuntimeException(e);
			}
			exchange.sendResponseHeaders(200, data.length);
			exchange.getResponseBody().write(data);
			exchange.getResponseBody().flush();
			exchange.getResponseBody().close();

		} else {
			exchange.sendResponseHeaders(403, -1);
		}
	}

}
