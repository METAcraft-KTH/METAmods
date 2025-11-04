package nu.metacraft.moderation.exile;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import nu.metacraft.lib.util.METACodecs;
import nu.metacraft.moderation.METAcraftModeration;

import java.util.*;
import java.util.stream.Collectors;

public class ExileData extends SavedData {

	private final Map<String, ExileDefinition> exileDefinitions = new HashMap<>();
	private final Map<UUID, ExileDefinition> exiledPlayers = new HashMap<>();

	public static ExileData getInstance(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	private static Codec<ExileData> createCodec(MinecraftServer server) {
		return RecordCodecBuilder.create(
				instance -> instance.group(
						ExileDefinition.Serialized.CODEC.listOf().fieldOf("ExileDefinitions").forGetter(
								d -> d.exileDefinitions.values().stream().map(ExileDefinition::serialize).toList()
						),
						METACodecs.createListSerializedMap(
								UUIDUtil.AUTHLIB_CODEC.fieldOf("Player"),
								Codec.STRING.fieldOf("Exile"),
								HashMap::new
						).fieldOf("ExiledPlayers").forGetter(
								d -> d.exiledPlayers.entrySet().stream().map(
										e -> Pair.of(e.getKey(), e.getValue().getName())
								).collect(Collectors.toMap(Pair::getFirst, Pair::getSecond))
						)
				).apply(
						instance, new ExileData(server)::fromData
				)
		);
	}

	private static final SavedDataType<ExileData> TYPE = new SavedDataType<>(
			"metacraft-moderation-exile", ctx -> createNew(ctx.levelOrThrow().getServer()),
			ctx -> createCodec(ctx.levelOrThrow().getServer()), null
	);

	private static ExileData createNew(MinecraftServer server) {
		METAcraftModeration.LOGGER.info("No previous state found, setting default values");
		return new ExileData(server);
	}

	private ExileData fromData(
			List<ExileDefinition.Serialized> definitions,
			Map<UUID, String> players
	) {
		METAcraftModeration.LOGGER.info("Previous state found, loading values");

		for (var e : definitions) {
			var def = new ExileDefinition(server);
			def.setSaveFunction(this::setDirty);
			def.deserialize(e);
			exileDefinitions.put(def.getName(), def);
		}

		for (var e : players.entrySet()) {
			UUID player = e.getKey();
			var name = e.getValue();
			var def = exileDefinitions.get(name);
			if (def != null) {
				this.exiledPlayers.put(player, def);
			} else {
				server.services().nameToIdCache().get(player).map(NameAndId::name).ifPresentOrElse(playerName -> {
					METAcraftModeration.LOGGER.fatal(
							"Exile definition " + name + " did not exist. Player " + playerName + " is free from exile!"
					);
				}, () -> {
					METAcraftModeration.LOGGER.error(
							"Exile definition " + name + " did not exist, and a player exiled there had apparently never joined the server?"
					);
				});
			}
		}
		return this;
	}

	protected final MinecraftServer server;

	protected ExileData(MinecraftServer server) {
		this.server = server;
	}

	public void add(ExileDefinition def) {
		def.setSaveFunction(this::setDirty);
		exileDefinitions.put(def.getName(), def);
		setDirty();
	}

	public void remove(String defName) {
		var removed = exileDefinitions.remove(defName);
		if (removed != null) {
			exiledPlayers.values().removeIf(def -> def == removed);
		}
		setDirty();
	}

	public ExileDefinition get(String defName) {
		return exileDefinitions.get(defName);
	}

	public void setExile(UUID player, ExileDefinition exile) {
		var actualPlayer = server.getPlayerList().getPlayer(player);
		if (actualPlayer != null) {
			var prevExileState = exiledPlayers.get(player);
			if (prevExileState == null && exile != null) {
				server.getCommands().performPrefixedCommand(
						actualPlayer.createCommandSourceStack().withPermission(2).withSuppressedOutput(), exile.getExileCommand()
				);
			} else if (prevExileState != null && exile == null) {
				server.getCommands().performPrefixedCommand(
						actualPlayer.createCommandSourceStack().withPermission(2).withSuppressedOutput(), prevExileState.getPardonCommand()
				);
				prevExileState.onRemove((ServerPlayer & ExilePlayerData) actualPlayer);
			}
		}
		if (exile != null) {
			exiledPlayers.put(player, exile);
		} else {
			exiledPlayers.remove(player);
		}
		setDirty();
	}

	public void setExile(ServerPlayer player, ExileDefinition exile) {
		setExile(player.getUUID(), exile);
	}

	public void removeExile(ServerPlayer player) {
		setExile(player, null);
	}

	public void removeExile(UUID player) {
		setExile(player, null);
	}

	public Optional<ExileDefinition> getExile(ServerPlayer player) {
		return getExile(player.getUUID());
	}

	public Optional<ExileDefinition> getExile(UUID player) {
		return Optional.ofNullable(exiledPlayers.get(player));
	}

	public Collection<ExileDefinition> getAll() {
		return exileDefinitions.values();
	}

}
