package nu.metacraft.cutscenes.cutscene;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.mojang.datafixers.DataFixer;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.DisplaySlot;
import org.jetbrains.annotations.Nullable;
import org.pcollections.HashTreePSet;
import org.pcollections.PSet;
import nu.metacraft.cutscenes.Cutscenes;
import nu.metacraft.cutscenes.mixin.PlayerListAccessor;
import nu.metacraft.cutscenes.cutscene.world.CutsceneWorld;
import nu.metacraft.cutscenes.cutscene.world.CutsceneWorldData;
import nu.metacraft.cutscenes.transitions.DeltaTickTransition;
import nu.metacraft.cutscenes.transitions.HideOtherPlayersTransition;
import nu.metacraft.cutscenes.util.IntervalMap;
import nu.metacraft.cutscenes.registry.TransitionRegistry;
import nu.metacraft.cutscenes.transitions.TeleportTransition;
import nu.metacraft.cutscenes.transitions.Transition;
import nu.metacraft.core.util.RefContext;
import nu.metacraft.core.entity.entities.player_mob.PlayerMob;
import nu.metacraft.lib.util.METACodecs;
import nu.metacraft.lib.util.TaskScheduler;
import nu.metacraft.lib.util.error_reporters.LoggingErrorReporter;
import nu.metacraft.lib.util.helper.EntityTrackerHelper;
import nu.metacraft.lib.util.helper.PlayerDataHelper;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

public class CutsceneInstance implements AutoCloseable {

	private static Timer timer;

	public static final String CUTSCENE = "cutscene"; //Careful, this is used by the datafixer!

	public static final String PLAYER_ITEM = "player_item";
	public static final String PLAYER_DUMMY_TAG = "metacraft_cutscenes.is_player_dummy";

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			initTimer();
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			removeTimer();
		});
	}

	private static void initTimer() {
		if (timer == null) {
			timer = new Timer();
		}
	}

	private static void removeTimer() {
		timer.cancel();
		timer = null;
	}

	private static final Codec<Map<UUID, CompoundTag>> SAVED_DATA_CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, CompoundTag.CODEC);

	/**
	 * WARNING, to make this cutscene work you must also run
	 * {@link CutsceneInstance#finalizeParse(MinecraftServer)}!
	 */
	public static final Codec<CutsceneInstance> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
					Cutscene.CODEC.fieldOf("config").forGetter(a -> a.cutscene),
					IntervalMap.createCodec(TransitionRegistry.CODEC.fieldOf("transition")).fieldOf("transitions").forGetter(
							a -> a.transitions
					),
					Codec.INT.fieldOf("time").forGetter(a -> a.time),
					CutsceneWorldData.CODEC.fieldOf("data").forGetter(a -> a.world.save()),
					SAVED_DATA_CODEC.fieldOf("saved_players").forGetter(cutscene -> cutscene.savedPlayerData),
					METACodecs.createListSerializedMap(
							UUIDUtil.LENIENT_CODEC.fieldOf("player"), UUIDUtil.LENIENT_CODEC.fieldOf("dummy"), HashMap::new
					).optionalFieldOf("player_dummies", new HashMap<>()).forGetter(a -> a.playerDummies),
					Codec.BOOL.fieldOf("ended").forGetter(CutsceneInstance::isEnded),
					Level.RESOURCE_KEY_CODEC.fieldOf("dim").forGetter(CutsceneInstance::getDim)
			).apply(instance, CutsceneInstance::new)
	);

	private final Cutscene cutscene;
	private final IntervalMap<Transition> transitions;
	private int time = 0;
	private boolean ended = false;
	private CutsceneWorld world;
	private RefContext entityLessContext;
	private CutsceneWorldData data;
	private ResourceKey<Level> dim;
	private RemoveHandler onRemove = null;
	private Map<UUID, Entity> playerDummiesAtTheEnd = null;
	private boolean shouldPlayNextCutscene = true;

	private final Map<UUID, CompoundTag> savedPlayerData;
	private final BiMap<UUID, UUID> playerDummies;

	private PSet<ServerPlayer> players = HashTreePSet.empty();

	public CutsceneInstance(Cutscene cutscene, ServerLevel world) {
		this.cutscene = cutscene;
		this.transitions = cutscene.createTransitions();
		this.savedPlayerData = new HashMap<>();
		this.playerDummies = HashBiMap.create();
		setTargetWorld(cutscene.getEntryDim().map(
				dim -> world.getServer().getLevel(dim)
		).orElse(world));
	}

	protected CutsceneInstance(
			Cutscene cutscene, IntervalMap<Transition> transitions, int time, CutsceneWorldData data,
			Map<UUID, CompoundTag> savedPlayerData, Map<UUID, UUID> playerDummies,
			boolean ended, ResourceKey<Level> dim
	) {
		this.cutscene = cutscene;
		this.transitions = transitions;
		this.time = time;
		this.data = data;
		this.playerDummies = HashBiMap.create(playerDummies);
		this.savedPlayerData = new HashMap<>(savedPlayerData);
		this.ended = ended;
		this.dim = dim;
		getTransitions().getIntervalsAt(getCurrentTime()).forEach(this::setupSmooth);
	}

	public CutsceneWorld getCutsceneWorld() {
		return world;
	}

	public MinecraftServer getServer() {
		return world.getServer();
	}

	public LevelEntityGetter<Entity> getEntityLookup() {
		return world.getEntityManager().getLookup();
	}

	public boolean hasPlayers() {
		return !players.isEmpty();
	}

	public ResourceKey<Level> getDim() {
		return dim;
	}

	public Cutscene getCutscene() {
		return cutscene;
	}

	/**
	 * Updates the cutscenes internal dimension, making sure it does not crash the game.
	 * Running this function after decoding {@link CutsceneInstance#CODEC} is mandatory
	 * (not necessary when using {@link CutsceneInstance#CutsceneInstance(Cutscene, ServerLevel)}).
	 * @param server The minecraft server to get the dimension from.
	 */
	public void finalizeParse(MinecraftServer server) {
		setTargetWorld(server.getLevel(dim));
	}

	public void forAllPlayers(Consumer<ServerPlayer> playerAction) {
		players.forEach(playerAction);
	}

	public Set<ServerPlayer> getPlayers() {
		return players;
	}

	public boolean hasPlayer(ServerPlayer player) {
		return players.contains(player);
	}

	public void end(boolean playNextScene) {
		ended = true;
		shouldPlayNextCutscene = playNextScene;
		world.clear();
		removeCutscene();
		getCutscene().getFinishCommand().ifPresent(command -> {
			TaskScheduler.scheduleImmediately(
					getServer(), () -> {
						var source = getServer().getFunctions().getGameLoopSender();
						getServer().getCommands().performPrefixedCommand(source, command);
					}
			);
		});
	}

	public Optional<CutsceneInstance> createNextCutscene() {
		if (!shouldPlayNextCutscene) return Optional.empty();
		return cutscene.getNextCutscene(getServer()).map(
				scene -> {
					var newScene = new CutsceneInstance(scene, world.getActualWorld());
					newScene.savedPlayerData.putAll(this.savedPlayerData);
					newScene.getTransitions().getIntervals().forEach(interval ->
						interval.getObject().copyFromPreviousCutscene(this, newScene, interval)
					);
					return newScene;
				}
		);
	}

	/**
	 * Changes the dimension used by the cutscene.
	 * If fired during a cutscene, all players should be teleported to the given dimension.
	 * Has no effect if the cutscene is already in the given world.
	 * @param targetWorld The world the cutscene should play in.
	 */
	public void setTargetWorld(ServerLevel targetWorld) {
		if (this.world != null && this.world.getActualWorld() == targetWorld) return;
		var prev = this.world;
		this.world = new CutsceneWorld(targetWorld, this, data);
		entityLessContext = createRefContext(null);
		data = null;
		this.dim = world.dimension();
		players.forEach(world::addPlayer);
		if (prev != null) {
			world.transferFrom(prev);
		}
	}

	public void setRemoveHandler(RemoveHandler onRemove) {
		this.onRemove = onRemove;
	}

	public RandomSource getRandom() {
		return getCutsceneWorld().getRandom();
	}

	private static CompoundTag writeSafeData(ServerPlayer player) {
		try (var logging = LoggingErrorReporter.create(() -> "metacraft:CutsceneInstance#writeSafedata", Cutscenes.LOGGER)) {
			var writeView = TagValueOutput.createWithContext(logging, player.registryAccess());
			player.saveWithoutId(writeView);
			var nbt = writeView.buildResult();
			nbt.remove(CUTSCENE);
			return nbt;
		}
	}

	public boolean canAddPlayer(ServerPlayer player) {
		return world.isPlayerWorld(player) || getCurrentTarget(player).isPresent();
	}

	public boolean isPlayerHiddenFrom(ServerPlayer player, ServerPlayer target) {
		if (cutscene.hidePlayer() && players.contains(player) && !ended) {
			return true;
		}
		if (getTransitions().getValuesAt(getCurrentTime()).anyMatch(transition -> transition == HideOtherPlayersTransition.getInstance())) {
			return players.contains(target) && players.contains(player);
		}
		return false;
	}

	private boolean isValidTarget(net.minecraft.world.level.portal.TeleportTransition target) {
		return world.getActualWorld() == target.newLevel();
	}

	private Optional<net.minecraft.world.level.portal.TeleportTransition> getCurrentTarget(ServerPlayer player) {
		var transitionTarget = getTransitions().getValuesAt(getCurrentTime()).map(
				t -> t instanceof TeleportTransition tp ? tp.getTarget(this).filter(this::isValidTarget).orElse(null) : null
		).filter(
				Objects::nonNull
		).findAny();
		if (transitionTarget.isEmpty()) {
			return cutscene.getEntryPoint(player, this).filter(this::isValidTarget);
		}
		return transitionTarget;
	}

	protected CompoundTag getPlayerData(UUID id, DataFixer fixer) {
		var oldData = savedPlayerData.get(id);
		var newData = PlayerDataHelper.updatePlayerData(oldData, fixer);
		if (oldData != newData) {
			savedPlayerData.put(id, newData);
		}
		return newData;
	}

	public void addPlayerDummy(ServerPlayer player, Function<CompoundTag, Entity> entitySpawner) {
		var existing = playerDummies.get(player.getUUID());
		if (existing != null && getEntityLookup().get(existing) != null) {
			return;
		}
		var entity = entitySpawner.apply(getPlayerData(player.getUUID(), player.level().getServer().getFixerUpper()));
		if (entity == null) return;
		entity.getSelfAndPassengers().forEach(e -> {
			e.getTags().add(PLAYER_DUMMY_TAG);
			if (e instanceof PlayerMob) {
				playerDummies.put(player.getUUID(), e.getUUID());
			}
		});
	}

	public Optional<Entity> getPlayerDummyFor(ServerPlayer player) {
		if (playerDummiesAtTheEnd != null) {
			return Optional.of(playerDummiesAtTheEnd.get(player.getUUID()));
		}
		var dummy = playerDummies.get(player.getUUID());
		if (dummy == null) {
			return Optional.empty();
		}
		return Optional.ofNullable(getEntityLookup().get(dummy));
	}

	private void removeLead(ServerPlayer player) {
		player.level().getEntities(
				EntityTypeTest.forClass(Entity.class),
				entity -> entity instanceof Leashable leashable && leashable.isLeashed() && leashable.getLeashHolder() == player
		).forEach(entity -> {
			((Leashable) entity).removeLeash();
			if (!player.isCreative()) {
				var lead = new ItemStack(Items.LEAD);
				player.getInventory().add(lead);
				if (!lead.isEmpty()) {
					var leadEntity = new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), lead);
					leadEntity.setUnlimitedLifetime();
					player.level().addFreshEntity(leadEntity);
				}
			}
		});
	}

	public void addPlayer(ServerPlayer player) {
		if (world == null) {
			Cutscenes.LOGGER.error("Attempted to add player to cutscene before world initialized!");
			return;
		}
		removeLead(player);
		if (!savedPlayerData.containsKey(player.getUUID())) {
			PlayerDataHelper.detachPassengersBeforeSaving(player);
			savedPlayerData.put(player.getUUID(), writeSafeData(player));
			PlayerDataHelper.unloadPassengersAndVehicles(player);
			PlayerDataHelper.unloadFarawayEntities(player);
		}
		if (world != null && !world.isPlayerWorld(player)) {
			getCurrentTarget(player).ifPresent(player::teleport);
		}
		players = players.plus(player);
		world.addPlayer(player);
		transitions.getIntervalsAt(getCurrentTime()).forEach(interval -> {
			if (interval.getStart() != getCurrentTime()) {
				interval.getObject().activate(player, this, interval);
			}
		});
		world.getActualWorld().getChunkSource().move(player);


		swapScoreboards(player, world.getActualWorld().getScoreboard(), world.getScoreboard());
	}

	private void swapScoreboards(
			ServerPlayer player, ServerScoreboard old,
			ServerScoreboard newScoreboard
	) {
		if (old == newScoreboard) return;
		for (var team : old.getPlayerTeams()) {
			player.connection.send(ClientboundSetPlayerTeamPacket.createRemovePacket(team));
		}
		for (var slot : DisplaySlot.values()) {
			var ob = old.getDisplayObjective(slot);
			if (ob != null) {
				for (var p : old.getStopTrackingPackets(ob)) {
					player.connection.send(p);
				}
			}
		}
		((PlayerListAccessor) player.level().getServer().getPlayerList()).callUpdateEntireScoreboard(newScoreboard, player);
	}

	public static void loadPlayerData(
			ServerPlayer player, ValueInput data, boolean usePlayerDataPosition,
			Optional<net.minecraft.world.level.portal.TeleportTransition> exitPosOverride
	) {
		PlayerDataHelper.applyPlayerData(player, data, usePlayerDataPosition && exitPosOverride.isEmpty());
		exitPosOverride.ifPresent(teleportTarget -> player.getRootVehicle().teleport(teleportTarget));
	}

	private void removeCutscene() {
		if (onRemove != null) {
			onRemove.beforePlayerReset(this);
		}
		resetPlayers();
		if (onRemove != null) {
			onRemove.afterPlayerReset(this);
		}
		onRemove = null;
		players = HashTreePSet.empty();
	}

	public boolean skipNextCutscene(boolean isLeavingCutscene) {
		return cutscene.getNextCutscene(getServer()).isEmpty() || !shouldPlayNextCutscene || isLeavingCutscene;
	}

	protected void resetPlayer(ServerPlayer player, boolean isLeavingCutscene) {
		try (var logging = LoggingErrorReporter.create(() -> "metacraft:CutsceneInstance#resetPlayer", Cutscenes.LOGGER)) {
			if (cutscene.resetPlayerData()) {
				var data = getPlayerData(player.getUUID(), player.level().getServer().getFixerUpper());
				if (data == null) {
					data = new CompoundTag();
				}
				if (skipNextCutscene(isLeavingCutscene)) {
					var readView = TagValueInput.create(logging, player.registryAccess(), data);
					loadPlayerData(player, readView, cutscene.returnToStart(), cutscene.getExitPoint(player, this));
				}
			} else if (skipNextCutscene(isLeavingCutscene)) {
				net.minecraft.world.level.portal.TeleportTransition target = null;
				if (cutscene.hasExitPoint()) {
					target = cutscene.getExitPoint(player, this).orElseThrow();
				} else if (cutscene.returnToStart() && skipNextCutscene(isLeavingCutscene)) {
					target = Optional.ofNullable(getPlayerData(player.getUUID(), player.level().getServer().getFixerUpper())).flatMap(data -> {
						Vec3 pos = data.read("Pos", Vec3.CODEC).orElse(Vec3.ZERO);
						Vec3 velocity = data.read("Motion", Vec3.CODEC).orElse(Vec3.ZERO);
						Vec2 rotation = data.read("Rotation", Vec2.CODEC).orElse(Vec2.ZERO);
						var readView = TagValueInput.create(logging, player.registryAccess(), data);
						var dim = PlayerDataHelper.getWorld(player.level().getServer(), readView);
						return dim.map(world -> {
							return new net.minecraft.world.level.portal.TeleportTransition(
									world, pos, velocity,
									rotation.x, rotation.y, net.minecraft.world.level.portal.TeleportTransition.DO_NOTHING
							);
						});
					}).orElseGet(() -> player.findRespawnPositionAndUseSpawnBlock(true, net.minecraft.world.level.portal.TeleportTransition.DO_NOTHING));
				}
				if (target != null) {
					player.teleport(target);
				}

				if (savedPlayerData.containsKey(player.getUUID())) {
					var data = getPlayerData(player.getUUID(), player.level().getServer().getFixerUpper());
					var readView = TagValueInput.create(logging, player.registryAccess(), data);
					PlayerDataHelper.loadRootVehicleAndPassengers(player, readView, e -> {
						if (player.level().addFreshEntity(e)) {
							e.absSnapTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
							return e;
						}
						return null;
					});
					if (target != null) {
						var vehicle = player.getRootVehicle();
						vehicle.teleport(
								target.withPosition(target.position().subtract(player.position().subtract(vehicle.position())))
						);
					}
					player.loadAndSpawnEnderPearls(readView);
				}

			}
		}
		if (skipNextCutscene(isLeavingCutscene)) {
			savedPlayerData.remove(player.getUUID());
		}

		player.level().getServer().getPlayerList().sendAllPlayerInfo(player);
	}

	public void resetPlayers() {
		forAllPlayers(p -> {
			removePlayer(p, false);
			resetPlayer(p, false);
		});
	}

	protected void disableTransitions(ServerPlayer player) {
		transitions.getIntervalsAt(getCurrentTime()).forEach(interval -> {
			interval.getObject().deactivate(player, this, interval);
		});
	}

	public void removePlayer(ServerPlayer player, boolean isLeavingCutscene) {
		disableTransitions(player);
		players = players.minus(player);
		if (world != null) {
			world.removePlayer(player, isLeavingCutscene);
		}

		if (cutscene.hidePlayer()) {
			var tracker = EntityTrackerHelper.getEntityTrackers(player.level()).get(player.getId());
			for (var p : getServer().getPlayerList().getPlayers()) {
				if (p != player) {
					tracker.updatePlayer(p);
				}
			}
		}
		swapScoreboards(player, world.getScoreboard(), world.getActualWorld().getScoreboard());
	}

	private void setupSmooth(IntervalMap.Interval<Transition> interval) {
		if (interval.getObject() instanceof DeltaTickTransition deltaTick) {
			if (deltaTick.getTask() != null) return;
			TimerTask task = new TimerTask() {

				private long prev = -1;

				@Override
				public void run() {
					var currentTime = System.currentTimeMillis();
					if (shouldTick()) {
						deltaTick.setProgress(deltaTick.getProgress() + (prev == -1 ? 0 : currentTime - prev));
					}
					float delta = Math.min((float) deltaTick.getProgress() / (interval.getLength() * 50), 1);
					deltaTick.tickDelta(CutsceneInstance.this, interval, delta);
					prev = currentTime;
				}
			};
			deltaTick.setTask(task);
			timer.schedule(task, 0, deltaTick.getInterval());
		}
	}

	private void deactivateSmooth(IntervalMap.Interval<Transition> interval) {
		if (interval.getObject() instanceof DeltaTickTransition deltaTick) {
			var ticker = deltaTick.getTask();
			if (ticker != null) {
				ticker.cancel();
			}
		}
	}

	public void onEntityRemoved(Entity entity) {
		var dummyToPlayer = playerDummies.inverse();
		if (dummyToPlayer.containsKey(entity.getUUID())) {
			var playerID = dummyToPlayer.get(entity.getUUID());
			if (!ended) {
				var player = getServer().getPlayerList().getPlayer(playerID);
				if (player == null || !players.contains(player)) {
					dummyToPlayer.remove(entity.getUUID());
				}
			} else {
				if (playerDummiesAtTheEnd == null) {
					playerDummiesAtTheEnd = new HashMap<>();
				}
				playerDummiesAtTheEnd.put(playerID, entity);
			}
		}
	}

	public boolean shouldTick() {
		return hasPlayers();
	}

	public void tick() {
		if (ended) {
			return;
		}
		if (!shouldTick()) return;
		if (world == null) {
			Cutscenes.LOGGER.error("Cutscene did not have a world, ending it prematurely! If you get this error, some developer forgot to call CutsceneInstance#setTargetWorld or CutsceneInstance#setWorldFromDim");
			end(false);
			return;
		}
		transitions.getIntervalsAt(time).forEach(
				interval -> {
					if (interval.getStart() == time) {
						interval.getObject().activate(this, interval);
						forAllPlayers(player -> interval.getObject().activate(player, this, interval));
						setupSmooth(interval);
					}
					interval.getObject().tick(this, interval);
					if (interval.getEnd() == time) {
						deactivateSmooth(interval);
						interval.getObject().deactivate(this, interval);
						forAllPlayers(player -> interval.getObject().deactivate(player, this, interval));
					}
				}
		);
		time++;
		world.tick(() -> true);
		if (time > transitions.getEnd()) {
			end(true);
		}
	}

	public void sendToPlayers(Packet<?> packet) {
		forAllPlayers(p -> p.connection.send(packet));
	}

	public void addEntity(String id, Entity entity) {
		world.getEntityManager().addEntity(id, entity);
	}

	public Optional<Entity> getRootEntity(String id) {
		return world.getEntityManager().getRootEntity(id);
	}

	public Stream<Entity> getEntities(String id) {
		return world.getEntityManager().getEntities(id);
	}

	public Optional<String> getIDForEntity(Entity entity) {
		return world.getEntityManager().getIDForEntity(entity);
	}

	public IntervalMap<Transition> getTransitions() {
		return transitions;
	}

	public int getCurrentTime() {
		return time;
	}

	public boolean isEnded() {
		return ended;
	}

	@Override
	public void close() {
		getTransitions().getIntervalsAt(getCurrentTime()).forEach(this::deactivateSmooth);
	}

	public record QueueEntry(ServerPlayer player, Operation operation) {
		public enum Operation {
			ADD, REMOVE
		}
	}

	public interface RemoveHandler {
		void beforePlayerReset(CutsceneInstance cutscene);
		void afterPlayerReset(CutsceneInstance cutscene);
	}

	public static Optional<CutsceneInstance> getCutscene(RefContext ctx) {
		return ctx.world() instanceof CutsceneWorld w ? Optional.of(w.getCutscene()) : Optional.empty();
	}

	public RefContext createRefContext(@Nullable Entity entity) {
		return new RefContext(
				Optional.ofNullable(entity), world, getRandom()
		);
	}

	public RefContext getRefContext() {
		return entityLessContext;
	}
}
