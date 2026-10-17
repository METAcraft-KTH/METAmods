package metacraft.ovvar.pack;

import eu.pb4.polymer.autohost.api.AutoHostUtils;
import eu.pb4.polymer.autohost.api.ResourcePackDataProvider;
import eu.pb4.polymer.autohost.impl.providers.ExternalProvider;
import metacraft.ovvar.Ovvar;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * The shaderpack patcher (tools/shaderpatcher, built into this mod's resources) for players who play
 * with a shaderpack: Polymer's autohost serves it beside the resource pack, on the game port, and
 * {@code /ovvar shaders} links it. The jar's hash is part of its path, because autohost tells
 * browsers to cache for a year.
 */
public final class ShaderPatcher {
	public static final String RESOURCE = "assets/ovvar/shaderpatcher/OvvarShaderPatcher.jar";
	private static @Nullable Identifier hosted;

	private ShaderPatcher() {}

	public static void init() {
		if (!FabricLoader.getInstance().isModLoaded("polymer-autohost")) return;
		Path jar = FabricLoader.getInstance().getModContainer(Ovvar.MOD_ID).flatMap(mod -> mod.findPath(RESOURCE)).orElse(null);
		if (jar == null) {
			Ovvar.LOGGER.warn("[{}] {} is missing from the mod; /ovvar shaders can only point into the resource pack", Ovvar.MOD_ID, RESOURCE);
			return;
		}
		try {
			String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(jar)), 0, 4);
			hosted = Identifier.fromNamespaceAndPath(Ovvar.MOD_ID, "shaderpatcher/" + hash + "/ovvar-shader-patcher.jar");
			AutoHostUtils.registerHostedFile(hosted, jar);
		} catch (IOException | NoSuchAlgorithmException e) {
			Ovvar.LOGGER.warn("[{}] could not host the shader patcher", Ovvar.MOD_ID, e);
		}
	}

	/**
	 * Where this player can download the patcher: the address they joined with, as for the resource
	 * pack. Empty when the server doesn't serve files itself (autohost off, or the pack hosted elsewhere).
	 */
	public static Optional<String> url(ServerPlayer player) {
		if (hosted == null) return Optional.empty();
		ResourcePackDataProvider provider = ResourcePackDataProvider.getActive();
		if (provider instanceof ExternalProvider) return Optional.empty();
		String url = provider.getFilePath(player.connection.getPacketContext(), hosted);
		if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) return Optional.empty();
		// autohost names files "<id>+pack.zip" and drops what follows the '+' when it serves one; a .jar
		// is what a browser should save
		int plus = url.lastIndexOf('+');
		return Optional.of(plus > url.lastIndexOf('/') ? url.substring(0, plus) : url);
	}
}
