package nu.metacraft.cutscenes.cutscene;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import nu.metacraft.cutscenes.Cutscenes;
import nu.metacraft.lib.util.SavedDataTypeCache;
import org.jetbrains.annotations.NotNull;
import org.pcollections.HashTreePMap;
import org.pcollections.PMap;
import nu.metacraft.cutscenes.CutsceneDataFixer;
import nu.metacraft.cutscenes.util.PGeneralMultimap;
import nu.metacraft.cutscenes.util.PMultimap;
import nu.metacraft.cutscenes.util.helper.CutsceneHelper;

import java.util.*;
import java.util.function.Consumer;

public class MultiplayerCutsceneManager extends SavedData {

	public static final String CUTSCENES = "cutscenes";
	public static final String OFFLINE_PLAYERS_KEY = "offline-players";

	private static final Codec<Map<String, CutsceneInstance>> CUTSCENE_LIST = Codec.unboundedMap(Codec.STRING, CutsceneInstance.CODEC);
	private static final Codec<Map<UUID, String>> PLAYER_TO_CUTSCENE = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.STRING);
	private static final Codec<Map<UUID, CutsceneInstance>> OFFLINE_PLAYERS = Codec.unboundedMap(UUIDUtil.STRING_CODEC, CutsceneInstance.CODEC);

	private static final SavedDataTypeCache.Type<@NotNull MultiplayerCutsceneManager, MinecraftServer> TYPE = new SavedDataTypeCache.Type<>(
			server -> new SavedDataType<>(
					Cutscenes.getID("multiplayer_cutscene_manager"),
					() -> new MultiplayerCutsceneManager(server),
					createCodec(server),
					DataFixTypes.METACRAFT_SAVED_DATA_MULTIPLAYER_CUTSCENE_MANAGER
			)
	);

	private static SavedDataType<@NotNull MultiplayerCutsceneManager> getType(MinecraftServer server) {
		return SavedDataTypeCache.get(server, TYPE);
	}

	private static Codec<MultiplayerCutsceneManager> createCodec(MinecraftServer server) {
		return RecordCodecBuilder.create(
				instance -> instance.group(
						CUTSCENE_LIST.optionalFieldOf(CUTSCENES, Map.of()).forGetter(t -> t.activeCutscenes),
						PLAYER_TO_CUTSCENE.optionalFieldOf("player-to-cutscene", Map.of()).forGetter(t -> t.cutsceneByPlayer),
						OFFLINE_PLAYERS.optionalFieldOf(OFFLINE_PLAYERS_KEY, Map.of()).forGetter(t -> t.disconnectedPlayers)
				).apply(instance, new MultiplayerCutsceneManager(server)::load)
		);
	}

	private MultiplayerCutsceneManager load(
			Map<String, CutsceneInstance> cutscenes, Map<UUID, String> playerMap, Map<UUID, CutsceneInstance> players
	) {
		activeCutscenes = HashTreePMap.from(cutscenes);
		activeCutscenes.forEach((name, scene) -> {
			scene.finalizeParse(server);
			scene.setRemoveHandler(getRemoveHandler(name));
		});
		playerMap.forEach((player, name) -> {
			if (activeCutscenes.containsKey(name)) {
				addPlayer(name, activeCutscenes.get(name), player);
			}
		});
		players.values().forEach(scene -> {
			scene.finalizeParse(server);
		});
		disconnectedPlayers.putAll(players);
		return this;
	}

	public static MultiplayerCutsceneManager getInstance(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(getType(server));
	}

	private final MinecraftServer server;

	private PMap<String, CutsceneInstance> activeCutscenes = HashTreePMap.empty();
	private final Map<UUID, String> cutsceneByPlayer = new HashMap<>();
	private final Map<UUID, CutsceneInstance> cutsceneByPlayerActive = new HashMap<>();
	private PMultimap<String, UUID> playerByCutscene = PGeneralMultimap.emptyHashBased();
	private final Map<UUID, CutsceneInstance> disconnectedPlayers = new HashMap<>();

	private MultiplayerCutsceneManager(MinecraftServer server) {
		this.server = server;
	}

	public Optional<CutsceneInstance> getCutscene(String cutscene) {
		return Optional.ofNullable(activeCutscenes.get(cutscene));
	}

	public Optional<CutsceneInstance> getCutsceneFromPlayer(ServerPlayer player) {
		return Optional.ofNullable(cutsceneByPlayerActive.get(player.getUUID()));
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
		playerByCutscene = playerByCutscene.plus(cutscene, player);
		setDirty();
	}

	private void removePlayer(UUID player) {
		var name = cutsceneByPlayer.remove(player);
		cutsceneByPlayerActive.remove(player);
		playerByCutscene = playerByCutscene.minus(name, player);
		setDirty();
	}

	public void addToCutscene(String cutscene, ServerPlayer player) {
		if (CutsceneHelper.isInPlayerSpecificCutscene(player)) {
			return;
		}
		getCutscene(cutscene).ifPresent(scene -> {
			if (cutsceneByPlayerActive.get(player.getUUID()) == scene || !scene.canAddPlayer(player)) {
				return;
			} else {
				removeFromCutscene(player);
			}
			scene.addPlayer(player);
			addPlayer(cutscene, scene, player.getUUID());
		});
	}

	private CutsceneInstance.RemoveHandler getRemoveHandler(String name) {
		return new CutsceneInstance.RemoveHandler() {

			Consumer<ServerPlayer> playerAction = p -> removePlayer(p.getUUID());
			List<ServerPlayer> players = null;

			@Override
			public void beforePlayerReset(CutsceneInstance cutscene) {
				cutscene.createNextCutscene().ifPresentOrElse(
						newScene -> {
							newScene.setRemoveHandler(getRemoveHandler(name));
							activeCutscenes = activeCutscenes.plus(name, newScene);
							players = new ArrayList<>();
							playerAction = playerAction.andThen(p -> players.add(p));
						},
						() -> activeCutscenes = activeCutscenes.minus(name)
				);
				cutscene.forAllPlayers(playerAction);
				playerByCutscene.get(name).forEach(player -> {
					if (players == null) {
						disconnectedPlayers.put(player, cutscene);
						removePlayer(player);
					} else {
						cutsceneByPlayerActive.put(player, activeCutscenes.get(name));
					}
				});
				setDirty();
			}

			@Override
			public void afterPlayerReset(CutsceneInstance cutscene) {
				if (players != null) {
					for (var p : players) {
						addToCutscene(name, p);
					}
				}
			}
		};
	}

	public void endCutscene(String cutscene) {
		if (activeCutscenes.containsKey(cutscene)) {
			activeCutscenes.get(cutscene).end(false);
			activeCutscenes = activeCutscenes.minus(cutscene);
			setDirty();
		}
	}

	public void addCutscene(String name, Cutscene cutscene, ServerLevel world) {
		var scene = new CutsceneInstance(cutscene, world);
		scene.setRemoveHandler(getRemoveHandler(name));
		activeCutscenes = activeCutscenes.plus(name, scene);
		setDirty();
	}

	public void leaveCutscene(ServerPlayer player) {
		removeFromCutscene(player);
	}


	protected void removeFromCutscene(ServerPlayer player) {
		var scene = cutsceneByPlayerActive.get(player.getUUID());
		if (scene != null) {
			scene.removePlayer(player, true);
			scene.resetPlayer(player, true);
		}
		removePlayer(player.getUUID());
	}

	public boolean isInCutscene(ServerPlayer player) {
		return cutsceneByPlayer.containsKey(player.getUUID());
	}

	public void onPlayerJoin(ServerPlayer player) {
		if (disconnectedPlayers.containsKey(player.getUUID())) {
			var scene = disconnectedPlayers.get(player.getUUID());
			scene.disableTransitions(player);
			scene.resetPlayer(player, true);
			disconnectedPlayers.remove(player.getUUID());
			setDirty();
		}
		if (cutsceneByPlayer.containsKey(player.getUUID())) {
			cutsceneByPlayerActive.get(player.getUUID()).addPlayer(player);
		}
	}

	public void onPlayerLeave(ServerPlayer player) {
		if (cutsceneByPlayer.containsKey(player.getUUID())) {
			cutsceneByPlayerActive.get(player.getUUID()).removePlayer(player, false);
		}
	}

	public void tick() {
		activeCutscenes.forEach((name, scene) -> {
			scene.tick();
			setDirty();
		});
	}
}
