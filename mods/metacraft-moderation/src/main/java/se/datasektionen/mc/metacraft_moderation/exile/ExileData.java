package se.datasektionen.mc.metacraft_moderation.exile;

import com.mojang.authlib.GameProfile;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.PersistentState;
import se.datasektionen.mc.metacraft_moderation.METAcraftModeration;

import java.util.*;

public class ExileData extends PersistentState {

	private static final String stateKey = "metacraft-moderation-exile";

	private static final String EXILE_DEFINITIONS = "ExileDefinitions";
	private static final String EXILED_PLAYERS = "ExiledPlayers";
	private static final String PLAYER = "Player";
	private static final String EXILE = "Exile";

	private final Map<String, ExileDefinition> exileDefinitions = new HashMap<>();
	private final Map<UUID, ExileDefinition> exiledPlayers = new HashMap<>();

	public static ExileData getInstance(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(getType(server), stateKey);
	}

	private static Type<ExileData> getType(MinecraftServer server) {
		return new Type<>(
				() -> createNew(server), (nbt, lookup) -> fromNbt(server, nbt, lookup), null
		);
	}

	private static ExileData createNew(MinecraftServer server) {
		METAcraftModeration.LOGGER.info("No previous state found, setting default values");
		return new ExileData(server);
	}

	private static ExileData fromNbt(MinecraftServer server, NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
		METAcraftModeration.LOGGER.info("Previous state found, loading values");
		ExileData settings = new ExileData(server);
		settings.readNbt(tag, lookup);
		return settings;
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

	public void readNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
		exileDefinitions.clear();
		NbtList exileDefs = tag.getList(EXILE_DEFINITIONS, NbtElement.COMPOUND_TYPE);
		for (NbtElement e : exileDefs) {
			var def = new ExileDefinition(server);
			def.setSaveFunction(this::markDirty);
			def.fromNBT((NbtCompound) e);
			exileDefinitions.put(def.getName(), def);
		}

		exiledPlayers.clear();
		NbtList exiledPlayers = tag.getList(EXILED_PLAYERS, NbtElement.COMPOUND_TYPE);
		for (NbtElement e : exiledPlayers) {
			NbtCompound entry = (NbtCompound) e;
			UUID player = entry.getUuid(PLAYER);
			var name = entry.getString(EXILE);
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
	}

	@Override
	public NbtCompound writeNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
		NbtList exileDefs = new NbtList();
		exileDefinitions.values().forEach(def -> exileDefs.add(def.toNBT()));
		tag.put(EXILE_DEFINITIONS, exileDefs);

		NbtList exiledPlayers = new NbtList();
		this.exiledPlayers.forEach((player, def) -> {
			NbtCompound entry = new NbtCompound();
			entry.putUuid(PLAYER, player);
			entry.putString(EXILE, def.getName());
			exiledPlayers.add(entry);
		});
		tag.put(EXILED_PLAYERS, exiledPlayers);

		return tag;
	}

}
