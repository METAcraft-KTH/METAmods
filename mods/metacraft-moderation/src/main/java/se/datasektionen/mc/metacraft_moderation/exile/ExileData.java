package se.datasektionen.mc.metacraft_moderation.exile;

import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Uuids;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;
import se.datasektionen.mc.metacraft_lib.util.ExtraCodecs;
import se.datasektionen.mc.metacraft_moderation.METAcraftModeration;

import java.util.*;
import java.util.stream.Collectors;

public class ExileData extends PersistentState {

	private final Map<String, ExileDefinition> exileDefinitions = new HashMap<>();
	private final Map<UUID, ExileDefinition> exiledPlayers = new HashMap<>();

	public static ExileData getInstance(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE);
	}

	private static Codec<ExileData> createCodec(MinecraftServer server) {
		return RecordCodecBuilder.create(
				instance -> instance.group(
						ExileDefinition.Serialized.CODEC.listOf().fieldOf("ExileDefinitions").forGetter(
								d -> d.exileDefinitions.values().stream().map(ExileDefinition::serialize).toList()
						),
						ExtraCodecs.createListSerializedMap(
								Uuids.CODEC.fieldOf("Player"),
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

	private static final PersistentStateType<ExileData> TYPE = new PersistentStateType<>(
			"metacraft-moderation-exile", ctx -> createNew(ctx.getWorldOrThrow().getServer()),
			ctx -> createCodec(ctx.getWorldOrThrow().getServer()), null
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
			def.setSaveFunction(this::markDirty);
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
				Optional.ofNullable(server.getUserCache()).flatMap(cache -> cache.getByUuid(player)).map(GameProfile::getName).ifPresentOrElse(playerName -> {
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
		def.setSaveFunction(this::markDirty);
		exileDefinitions.put(def.getName(), def);
		markDirty();
	}

	public void remove(String defName) {
		var removed = exileDefinitions.remove(defName);
		if (removed != null) {
			exiledPlayers.values().removeIf(def -> def == removed);
		}
		markDirty();
	}

	public ExileDefinition get(String defName) {
		return exileDefinitions.get(defName);
	}

	public void setExile(UUID player, ExileDefinition exile) {
		var actualPlayer = server.getPlayerManager().getPlayer(player);
		if (actualPlayer != null) {
			var prevExileState = exiledPlayers.get(player);
			if (prevExileState == null && exile != null) {
				server.getCommandManager().executeWithPrefix(
						actualPlayer.getCommandSource().withLevel(2).withSilent(), exile.getExileCommand()
				);
			} else if (prevExileState != null && exile == null) {
				server.getCommandManager().executeWithPrefix(
						actualPlayer.getCommandSource().withLevel(2).withSilent(), prevExileState.getPardonCommand()
				);
				prevExileState.onRemove((ServerPlayerEntity & ExilePlayerData) actualPlayer);
			}
		}
		if (exile != null) {
			exiledPlayers.put(player, exile);
		} else {
			exiledPlayers.remove(player);
		}
		markDirty();
	}

	public void setExile(ServerPlayerEntity player, ExileDefinition exile) {
		setExile(player.getUuid(), exile);
	}

	public void removeExile(ServerPlayerEntity player) {
		setExile(player, null);
	}

	public void removeExile(UUID player) {
		setExile(player, null);
	}

	public Optional<ExileDefinition> getExile(ServerPlayerEntity player) {
		return getExile(player.getUuid());
	}

	public Optional<ExileDefinition> getExile(UUID player) {
		return Optional.ofNullable(exiledPlayers.get(player));
	}

	public Collection<ExileDefinition> getAll() {
		return exileDefinitions.values();
	}

}
