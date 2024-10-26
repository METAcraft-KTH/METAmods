package se.datasektionen.mc.cutscenes.cutscene;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.mojang.serialization.Codec;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Uuids;
import net.minecraft.world.PersistentState;
import se.datasektionen.mc.cutscenes.CutsceneDataFixer;
import se.datasektionen.mc.cutscenes.Cutscenes;
import se.datasektionen.mc.cutscenes.util.helper.CutsceneHelper;

import java.util.*;

public class MultiplayerCutsceneManager extends PersistentState {

	private static final String key = "multiplayer-cutscene-manager";

	private static final String CUTSCENES = "cutscenes";
	private static final String PLAYER_TO_CUTSCENE_KEY = "player-to-cutscene";
	private static final String OFFLINE_PLAYERS_KEY = "offline-players";

	private static final Codec<Map<String, CutsceneInstance>> CUTSCENE_LIST = Codec.unboundedMap(Codec.STRING, CutsceneInstance.CODEC);
	private static final Codec<Map<UUID, String>> PLAYER_TO_CUTSCENE = Codec.unboundedMap(Uuids.STRING_CODEC, Codec.STRING);
	private static final Codec<Map<UUID, CutsceneInstance>> OFFLINE_PLAYERS = Codec.unboundedMap(Uuids.STRING_CODEC, CutsceneInstance.CODEC);

	private static Type<MultiplayerCutsceneManager> getType(MinecraftServer server) {
		return new Type<>(
				() -> new MultiplayerCutsceneManager(server),
				(nbt, lookup) -> MultiplayerCutsceneManager.fromNBT(server, nbt, lookup),
				CutsceneDataFixer.Types.SAVED_DATA_MULTIPLAYER_CUTSCENE_MANAGER
		);
	}

	private static MultiplayerCutsceneManager fromNBT(MinecraftServer server, NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		var manager = new MultiplayerCutsceneManager(server);
		manager.readNBT(nbt, lookup);
		return manager;
	}

	public static MultiplayerCutsceneManager getInstance(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(getType(server), key);
	}

	private final MinecraftServer server;

	private final Map<String, CutsceneInstance> activeCutscenes = new HashMap<>();
	private final Map<UUID, String> cutsceneByPlayer = new HashMap<>();
	private final Map<UUID, CutsceneInstance> cutsceneByPlayerActive = new HashMap<>();
	private final Multimap<String, UUID> playerByCutscene = HashMultimap.create();
	private final Map<UUID, CutsceneInstance> disconnectedPlayers = new HashMap<>();

	private MultiplayerCutsceneManager(MinecraftServer server) {
		this.server = server;
	}

	public Optional<CutsceneInstance> getCutscene(String cutscene) {
		return Optional.ofNullable(activeCutscenes.get(cutscene));
	}

	public Optional<CutsceneInstance> getCutsceneFromPlayer(ServerPlayerEntity player) {
		return Optional.ofNullable(cutsceneByPlayerActive.get(player.getUuid()));
	}

	public Collection<String> getCutsceneNames() {
		return activeCutscenes.keySet();
	}

	public Optional<String> getCutsceneName(CutsceneInstance instance) {
		for (var scene : activeCutscenes.entrySet()) {
			if (scene.getValue() == instance) {
				return Optional.of(scene.getKey());
			}
		}
		return Optional.empty();
	}

	private void addPlayer(String cutscene, CutsceneInstance scene, UUID player) {
		cutsceneByPlayer.put(player, cutscene);
		cutsceneByPlayerActive.put(player, scene);
		playerByCutscene.put(cutscene, player);
		markDirty();
	}

	private void removePlayer(UUID player) {
		var name = cutsceneByPlayer.remove(player);
		cutsceneByPlayerActive.remove(player);
		playerByCutscene.remove(name, player);
		markDirty();
	}

	public void addToCutscene(String cutscene, ServerPlayerEntity player) {
		if (CutsceneHelper.isInPlayerSpecificCutscene(player)) {
			return;
		}
		getCutscene(cutscene).ifPresent(scene -> {
			if (cutsceneByPlayerActive.get(player.getUuid()) == scene || !scene.canAddPlayer(player)) {
				return;
			} else {
				removeFromCutscene(player);
			}
			scene.addPlayer(player);
			addPlayer(cutscene, scene, player.getUuid());
		});
	}

	private void onCutsceneRemove(String name, CutsceneInstance cutscene) {
		cutscene.removeCutscene(p -> removePlayer(p.getUuid()));
		playerByCutscene.get(name).forEach(player -> {
			disconnectedPlayers.put(player, cutscene);
		});
		disconnectedPlayers.forEach((player, c) -> {
			removePlayer(player);
		});
		markDirty();
	}

	public void endCutscene(String cutscene) {
		if (activeCutscenes.containsKey(cutscene)) {
			activeCutscenes.get(cutscene).end();
			onCutsceneRemove(cutscene, activeCutscenes.get(cutscene));
			activeCutscenes.remove(cutscene);
			markDirty();
		}
	}

	public void addCutscene(String name, Cutscene cutscene, ServerWorld world) {
		var scene = new CutsceneInstance(cutscene, world);
		scene.setTargetWorld(world);
		activeCutscenes.put(name, scene);
		markDirty();
	}

	public void leaveCutscene(ServerPlayerEntity player) {
		removeFromCutscene(player);
	}


	protected void removeFromCutscene(ServerPlayerEntity player) {
		var scene = cutsceneByPlayerActive.get(player.getUuid());
		if (scene != null) {
			scene.removePlayer(player);
			scene.resetPlayer(player);
		}
		removePlayer(player.getUuid());
	}

	public boolean isInCutscene(ServerPlayerEntity player) {
		return cutsceneByPlayer.containsKey(player.getUuid());
	}

	public void onPlayerJoin(ServerPlayerEntity player) {
		if (disconnectedPlayers.containsKey(player.getUuid())) {
			var scene = disconnectedPlayers.get(player.getUuid());
			scene.addPlayer(player);
			scene.tick();
			scene.resetPlayers(p -> {});
			disconnectedPlayers.remove(player.getUuid());
			markDirty();
		}
		if (cutsceneByPlayer.containsKey(player.getUuid())) {
			cutsceneByPlayerActive.get(player.getUuid()).addPlayer(player);
		}
	}

	public void onPlayerLeave(ServerPlayerEntity player) {
		if (cutsceneByPlayer.containsKey(player.getUuid())) {
			cutsceneByPlayerActive.get(player.getUuid()).removePlayer(player);
		}
	}

	public void onServerShutdown() {
		for (var cutscene : activeCutscenes.values()) {
			cutscene.close();
		}
	}

	public void tick() {
		activeCutscenes.entrySet().removeIf(scene -> {
			scene.getValue().tick();
			markDirty();
			if (scene.getValue().isEnded()) {
				onCutsceneRemove(scene.getKey(), scene.getValue());
				return true;
			}
			return false;
		});
	}

	public void readNBT(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		if (nbt.contains(CUTSCENES)) {
			CUTSCENE_LIST.parse(lookup.getOps(NbtOps.INSTANCE), nbt.get(CUTSCENES)).resultOrPartial(
					Cutscenes.LOGGER::error
			).ifPresent(activeCutscenes::putAll);
			activeCutscenes.values().forEach(scene -> scene.finalizeParse(server));
		}
		if (nbt.contains(PLAYER_TO_CUTSCENE_KEY)) {
			PLAYER_TO_CUTSCENE.parse(lookup.getOps(NbtOps.INSTANCE), nbt.get(PLAYER_TO_CUTSCENE_KEY)).resultOrPartial(
					Cutscenes.LOGGER::error
			).ifPresent(playerMap -> {
				playerMap.forEach((player, name) -> {
					if (activeCutscenes.containsKey(name)) {
						addPlayer(name, activeCutscenes.get(name), player);
					}
				});
			});
		}
		if (nbt.contains(OFFLINE_PLAYERS_KEY)) {
			OFFLINE_PLAYERS.parse(lookup.getOps(NbtOps.INSTANCE), nbt.get(OFFLINE_PLAYERS_KEY)).resultOrPartial(
					Cutscenes.LOGGER::error
			).ifPresent(this.disconnectedPlayers::putAll);
		}
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		CUTSCENE_LIST.encodeStart(registryLookup.getOps(NbtOps.INSTANCE), activeCutscenes).resultOrPartial(
				Cutscenes.LOGGER::error
		).ifPresent(cutscenes -> {
			nbt.put(CUTSCENES, cutscenes);
		});
		PLAYER_TO_CUTSCENE.encodeStart(registryLookup.getOps(NbtOps.INSTANCE), cutsceneByPlayer).resultOrPartial(
				Cutscenes.LOGGER::error
		).ifPresent(playerMap -> {
			nbt.put(PLAYER_TO_CUTSCENE_KEY, playerMap);
		});
		OFFLINE_PLAYERS.encodeStart(registryLookup.getOps(NbtOps.INSTANCE), disconnectedPlayers).resultOrPartial(
				Cutscenes.LOGGER::error
		).ifPresent(playerMap -> {
			nbt.put(OFFLINE_PLAYERS_KEY, playerMap);
		});
		return nbt;
	}
}
