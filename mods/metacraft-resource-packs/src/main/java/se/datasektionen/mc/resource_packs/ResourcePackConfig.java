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

import java.io.File;
import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class ResourcePackConfig implements Modifiable, LoadAware {

	private static final HashFunction SHA1 = Hashing.sha1();

	private static final Path configDir = FabricLoader.getInstance().getConfigDir().resolve(ResourcePacks.MODID);
	public static final Path RESOURCE_PACK_DIR = configDir.resolve("resource-packs");
	public static final Path RP_UPDATE_DIR = RESOURCE_PACK_DIR.resolve("update");

	public static final Codec<ResourcePackConfig> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
					Codec.unboundedMap(Uuids.STRING_CODEC, ResourcePack.CODEC).fieldOf("resource_packs").forGetter(
							c -> c.resourcePacks
					),
					Codec.BOOL.fieldOf("required").forGetter(c -> c.required),
					TextCodecs.CODEC.optionalFieldOf("prompt").forGetter(c -> c.prompt),
					Codec.STRING.fieldOf("server_address").forGetter(c -> c.serverAddress),
					Codec.STRING.optionalFieldOf("network_address").forGetter(c -> c.networkAddress),
					Codec.intRange(0, 65535).fieldOf("port").forGetter(c -> c.port),
					Codec.INT.fieldOf("max_connections").forGetter(c -> c.maxConnections),
					ResourcePackServer.SSLSettings.CODEC.optionalFieldOf("ssl").forGetter(c -> c.sslSettings)
			).apply(instance, ResourcePackConfig::new)
	);

	private static final ConfigContainer<ResourcePackConfig> CONFIG = ConfigContainer.Builder.create(
			CODEC, configDir.resolve("config.json"),
			() -> new ResourcePackConfig()
	).setReloader((old, reloaded, cause) -> reloaded.get().map(c -> {
		old.getResourcePacks().forEach(pack -> {
			c.prevPacks.put(pack.getKey(), pack.getValue());
		});
		return c;
	}).orElse(old)).build();

	private final Map<UUID, ResourcePack> resourcePacks;
	private final Map<UUID, ResourcePack> prevPacks = new HashMap<>();
	private final boolean required;
	private final Optional<Text> prompt;
	private final String serverAddress;
	private final Optional<String> networkAddress;
	private final int port;
	private final int maxConnections;
	private final Optional<ResourcePackServer.SSLSettings> sslSettings;

	public ResourcePackConfig(
			Map<UUID, ResourcePack> resourcePacks,
			boolean required, Optional<Text> prompt, String serverAddress,
			Optional<String> networkAddress, int port, int maxConnections,
			Optional<ResourcePackServer.SSLSettings> sslSettings
	) {
		this.resourcePacks = resourcePacks instanceof ImmutableMap<UUID, ResourcePack> ? new HashMap<>(resourcePacks) : resourcePacks;
		this.required = required;
		this.prompt = prompt;
		this.serverAddress = serverAddress;
		this.networkAddress = networkAddress;
		this.port = port;
		this.maxConnections = maxConnections;
		this.sslSettings = sslSettings;
	}

	public ResourcePackConfig() {
		this(
				new HashMap<>(), true,
				Optional.empty(), "localhost",
				Optional.empty(), 25585,
				50, Optional.empty()
		);
	}

	public static ResourcePackConfig getConfig() {
		return CONFIG.get();
	}

	public static void reload() {
		CONFIG.reload();
	}

	public boolean resourcePackExists(UUID uuid) {
		return resourcePacks.containsKey(uuid);
	}

	public ResourcePack getResourcePack(UUID uuid) {
		return resourcePacks.get(uuid);
	}

	private boolean exists(UUID pack) {
		return prevPacks.containsKey(pack) && resourcePacks.containsKey(pack);
	}

	public boolean hasChanged(UUID pack) {
		if (!prevPacks.containsKey(pack) && resourcePacks.containsKey(pack)) return true;
		return exists(pack) && !Objects.equals(prevPacks.get(pack).getHash(), resourcePacks.get(pack).getHash());
	}

	public boolean isNowGlobal(UUID pack) {
		return exists(pack) && !prevPacks.get(pack).isGlobal() && resourcePacks.get(pack).isGlobal();
	}

	public Collection<Map.Entry<UUID, ResourcePack>> getResourcePacks() {
		return Collections.unmodifiableSet(resourcePacks.entrySet());
	}

	public Iterable<UUID> getRemovedPacks() {
		return prevPacks.keySet().stream().filter(resourcePack -> !resourcePacks.containsKey(resourcePack))::iterator;
	}

	public Iterable<UUID> getPrevGlobals() {
		return prevPacks.entrySet().stream().filter(
				pack -> resourcePacks.containsKey(pack.getKey()) && pack.getValue().isGlobal() && !resourcePacks.get(pack.getKey()).isGlobal()
		).map(Map.Entry::getKey)::iterator;
	}

	public String getServerAddress() {
		return serverAddress;
	}

	public int getPort() {
		return port;
	}

	public ResourcePackServer createResourcePackServer(MinecraftServer server) throws UnknownHostException {
		return new ResourcePackServer(server, port, networkAddress, maxConnections, sslSettings);
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
		String protocol = sslSettings.isPresent() ? "https" : "http";
		return new ResourcePackSendS2CPacket(
				uuid,  protocol + "://" + getServerAddress() + ":" + getPort()+ "/" + uuid.toString(),
				entry.getHash().toString(), required, prompt
		);
	}

	@Override
	public void afterLoad(Optional<ReloadCause> cause) {
		try {
			Files.createDirectories(RP_UPDATE_DIR);
			var zipsToUpdate = RP_UPDATE_DIR.toFile().listFiles(file -> file.getName().endsWith(".zip"));
			for (File file : zipsToUpdate) {
				Path dest = RESOURCE_PACK_DIR.resolve(file.getName());
				ResourcePacks.LOGGER.info("Moving {} to {}", file, dest);
				Files.move(file.toPath(), dest, StandardCopyOption.REPLACE_EXISTING);
			}
			var resourcePackZips = RESOURCE_PACK_DIR.toFile().listFiles(file -> file.getName().endsWith(".zip"));
			if (resourcePackZips != null) {
				for (var file : resourcePackZips) {
					var path = file.toPath();
					var hash = AccessorNetworkUtils.callHash(path, SHA1);
					UUID uuid = UUID.randomUUID();
					CONFIG.modify(c -> {
						if (c.resourcePacks.containsKey(uuid)) {
							//Duplicate
							ResourcePacks.LOGGER.warn(
									"Resource pack {} tried to use UUID {} " +
											"but it was already present! This resource pack " +
											"must be added to config manually.", file, uuid
							);
						}

						for (var pack : c.resourcePacks.values()) {
							//Resource pack update.
							if (pack.getFile().equals(path)) {
								pack.setHash(hash);
								return false;
							}
							//Try to follow renamed file (might cause mismatches).
							if ((pack.getHash() == null || pack.getHash().equals(hash)) && !pack.getFile().toFile().exists()) {
								pack.setFile(path);
								pack.setHash(hash);
								return true;
							}
						}

						c.resourcePacks.put(uuid, new ResourcePack(
								path, false, hash
						));
						return true;
					});
					CONFIG.get();
				}
			}
		} catch (IOException e) {
			ResourcePacks.LOGGER.error(e);
		}

		if (cause.isPresent()) {
			ResourcePackServerManager.getServers().forEach(ResourcePackHelper::resendResourcePacks);
		}
	}

	private Set<UUID> getGlobalPacks() {
		return resourcePacks.entrySet().stream().filter(
				entry -> entry.getValue().isGlobal()
		).map(Map.Entry::getKey).collect(Collectors.toSet());
	}

	public static class ResourcePack {

		private Path file;
		private final boolean global;
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
						Codec.BOOL.fieldOf("global").forGetter(ResourcePack::isGlobal)
				).apply(instance, ResourcePack::new)
		);

		public ResourcePack(Path file, boolean global) {
			this.file = file;
			this.global = global;
		}

		public ResourcePack(Path file, boolean global, HashCode hash) {
			this(file, global);
			this.hash = hash;
		}

		public HashCode getHash() {
			return hash;
		}

		public void setFile(Path path) {
			this.file = path;
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
	}
}
