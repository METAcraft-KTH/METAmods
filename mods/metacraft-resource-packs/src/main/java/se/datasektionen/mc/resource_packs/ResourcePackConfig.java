package se.datasektionen.mc.resource_packs;

import com.google.common.collect.ImmutableMap;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.packet.s2c.common.ResourcePackSendS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.Uuids;
import se.datasektionen.mc.resource_packs.mixin.AccessorNetworkUtils;
import se.datasektionen.mc.metacraft_lib.config.container.ConfigContainer;
import se.datasektionen.mc.metacraft_lib.config.container.ReloadCause;
import se.datasektionen.mc.metacraft_lib.config.extensions.LoadAware;
import se.datasektionen.mc.metacraft_lib.config.extensions.Modifiable;

import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class ResourcePackConfig implements Modifiable, LoadAware {

	private static final HashFunction SHA1 = Hashing.sha1();

	private static final Path configDir = FabricLoader.getInstance().getConfigDir().resolve(ResourcePacks.MODID);
	public static final Path RESOURCE_PACK_DIR = configDir.resolve("resource-packs");

	public static final Codec<ResourcePackConfig> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
					Codec.unboundedMap(Uuids.STRING_CODEC, ResourcePack.CODEC).fieldOf("resource_packs").forGetter(
							c -> c.resourcePacks
					),
					Codec.STRING.fieldOf("server_address").forGetter(c -> c.serverAddress),
					Codec.intRange(0, 65535).fieldOf("port").forGetter(c -> c.port),
					Codec.INT.fieldOf("max_connections").forGetter(c -> c.maxConnections)
			).apply(instance, ResourcePackConfig::new)
	);

	private static final ConfigContainer<ResourcePackConfig> CONFIG = ConfigContainer.Builder.create(
			CODEC, configDir.resolve("config.json"),
			() -> new ResourcePackConfig()
	).build();

	private final Map<UUID, ResourcePack> resourcePacks;
	private final String serverAddress;
	private final int port;
	private final int maxConnections;

	public ResourcePackConfig(Map<UUID, ResourcePack> resourcePacks, String serverAddress, int port, int maxConnections) {
		this.resourcePacks = resourcePacks instanceof ImmutableMap<UUID, ResourcePack> ? new HashMap<>(resourcePacks) : resourcePacks;
		this.serverAddress = serverAddress;
		this.port = port;
		this.maxConnections = maxConnections;
	}

	public ResourcePackConfig() {
		this(new HashMap<>(), "localhost", 25585, 50);
	}

	public static ResourcePackConfig getConfig() {
		return CONFIG.get();
	}

	public static void reload() {
		CONFIG.reload();
	}

	public ResourcePack getResourcePack(UUID uuid) {
		return resourcePacks.get(uuid);
	}

	public Collection<Map.Entry<UUID, ResourcePack>> getResourcePacks() {
		return Collections.unmodifiableSet(resourcePacks.entrySet());
	}

	public String getServerAddress() {
		return serverAddress;
	}

	public int getPort() {
		return port;
	}

	public ResourcePackServer createResourcePackServer(MinecraftServer server) throws UnknownHostException {
		return new ResourcePackServer(server, port, serverAddress, maxConnections);
	}

	private boolean modified = false;

	@Override
	public void setModified(boolean modified) {
		this.modified = modified;
	}

	@Override
	public boolean isModified() {
		return modified;
	}

	public ResourcePackSendS2CPacket createEnablePacket(UUID uuid) {
		var entry = getResourcePack(uuid);
		if (entry == null) return null;
		return new ResourcePackSendS2CPacket(
				uuid, "http://" + getServerAddress() + ":" + getPort()+ "/" + uuid.toString(),
				entry.getHash().toString(), entry.isRequired(), entry.getPrompt()
		);
	}

	@Override
	public void afterLoad(Optional<ReloadCause> optional) {
		try {
			var resourcePackZips = RESOURCE_PACK_DIR.toFile().listFiles(file -> file.getName().endsWith(".zip"));
			if (resourcePackZips != null) {
				for (var file : resourcePackZips) {
					var path = file.toPath();
					var hash = AccessorNetworkUtils.callHash(path, SHA1);
					UUID uuid = UUID.nameUUIDFromBytes(("METAcraftResourcePack:" + file.toString()).getBytes(StandardCharsets.UTF_8));
					CONFIG.modify(c -> {
						if (c.resourcePacks.containsKey(uuid)) {
							if (c.resourcePacks.get(uuid).file.equals(path)) {
								//Resource pack update.
								var pack = c.resourcePacks.get(uuid);
								if (!hash.equals(pack.getHash())) {
									pack.setHash(hash);
								}
								return false;
							} else {
								//Duplicate
								ResourcePacks.LOGGER.warn(
										"Resource pack {} tried to use UUID {} " +
												"but it was already present! This resource pack " +
												"must be added to config manually.", file, uuid
								);
							}
						}

						//Try to follow renaming.
						for (var pack : c.resourcePacks.values()) {
							if (pack.getFile().equals(path)) {
								pack.setHash(hash);
								return false;
							}
							if ((pack.getHash() == null || pack.getHash().equals(hash)) && !pack.getFile().toFile().exists()) {
								pack.setFile(path);
								pack.setHash(hash);
								return true;
							}
						}

						c.resourcePacks.put(uuid, new ResourcePack(
								path, false, true, Optional.empty(), hash
						));
						return true;
					});
					CONFIG.get();
				}
			}
		} catch (IOException e) {
			ResourcePacks.LOGGER.error(e);
		}
	}

	public static class ResourcePack {

		private Path file;
		private final boolean global;
		private final boolean required;
		private final Optional<Text> prompt;
		private HashCode hash;

		public static final Codec<ResourcePack> CODEC = RecordCodecBuilder.create(
				instance -> instance.group(
						Codec.STRING.comapFlatMap(file -> {
							try {
								return DataResult.success(Path.of(file));
							} catch (InvalidPathException e) {
								return DataResult.error(e::getMessage);
							}
						}, Path::toString).fieldOf("file").forGetter(ResourcePack::getFile),
						Codec.BOOL.fieldOf("global").forGetter(ResourcePack::isGlobal),
						Codec.BOOL.fieldOf("required").forGetter(ResourcePack::isRequired),
						TextCodecs.CODEC.optionalFieldOf("prompt").forGetter(ResourcePack::getPrompt)
				).apply(instance, ResourcePack::new)
		);

		public ResourcePack(Path file, boolean global, boolean required, Optional<Text> prompt) {
			this.file = file;
			this.global = global;
			this.required = required;
			this.prompt = prompt;
		}

		public ResourcePack(Path file, boolean global, boolean required, Optional<Text> prompt, HashCode hash) {
			this(file, global, required, prompt);
			this.hash = hash;
		}

		public HashCode getHash() {
			return hash;
		}

		public void setFile(Path path) {
			this.file = path;
		}

		public Optional<Text> getPrompt() {
			return prompt;
		}

		public void setHash(HashCode hash) {
			this.hash = hash;
		}

		public Path getFile() {
			return file;
		}

		public boolean isGlobal() {
			return global;
		}

		public boolean isRequired() {
			return required;
		}
	}
}
