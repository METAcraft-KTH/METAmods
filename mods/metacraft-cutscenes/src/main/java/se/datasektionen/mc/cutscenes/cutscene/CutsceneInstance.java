package se.datasektionen.mc.cutscenes.cutscene;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.entity.*;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.*;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.TeamS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import net.minecraft.world.entity.EntityLookup;
import org.pcollections.HashTreePSet;
import org.pcollections.PSet;
import se.datasektionen.mc.cutscenes.Cutscenes;
import se.datasektionen.mc.cutscenes.mixin.AccessorPlayerManager;
import se.datasektionen.mc.cutscenes.cutscene.world.CutsceneWorld;
import se.datasektionen.mc.cutscenes.cutscene.world.CutsceneWorldData;
import se.datasektionen.mc.cutscenes.transitions.DeltaTickTransition;
import se.datasektionen.mc.cutscenes.transitions.HideOtherPlayersTransition;
import se.datasektionen.mc.cutscenes.util.IntervalMap;
import se.datasektionen.mc.cutscenes.registry.TransitionRegistry;
import se.datasektionen.mc.cutscenes.transitions.TeleportTransition;
import se.datasektionen.mc.cutscenes.transitions.Transition;
import se.datasektionen.mc.metacraft_core.entity.entities.player_mob.PlayerMob;
import se.datasektionen.mc.metacraft_lib.util.ExtraCodecs;
import se.datasektionen.mc.metacraft_lib.util.TaskScheduler;
import se.datasektionen.mc.metacraft_lib.util.helper.EntityTrackerHelper;
import se.datasektionen.mc.metacraft_lib.util.helper.PlayerDataHelper;

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

	private static final Codec<Map<UUID, NbtCompound>> SAVED_DATA_CODEC = Codec.unboundedMap(Uuids.STRING_CODEC, NbtCompound.CODEC);

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
					SAVED_DATA_CODEC.fieldOf("saved_players").forGetter(cutscene -> cutscene.savedPlayerData), //Careful, this is used by the datafixer!
					ExtraCodecs.createListSerializedMap(
							Uuids.STRICT_CODEC.fieldOf("player"), Uuids.STRICT_CODEC.fieldOf("dummy"), HashMap::new
					).optionalFieldOf("player_dummies", new HashMap<>()).forGetter(a -> a.playerDummies),
					Codec.BOOL.fieldOf("ended").forGetter(CutsceneInstance::isEnded),
					World.CODEC.fieldOf("dim").forGetter(CutsceneInstance::getDim)
			).apply(instance, CutsceneInstance::new)
	);

	private final Cutscene cutscene;
	private final IntervalMap<Transition> transitions;
	private int time = 0;
	private boolean ended = false;
	private CutsceneWorld world;
	private CutsceneWorldData data;
	private RegistryKey<World> dim;
	private RemoveHandler onRemove = null;
	private Map<UUID, Entity> playerDummiesAtTheEnd = null;
	private boolean shouldPlayNextCutscene = true;

	private final Map<UUID, NbtCompound> savedPlayerData;
	private final BiMap<UUID, UUID> playerDummies;

	private PSet<ServerPlayerEntity> players = HashTreePSet.empty();

	public CutsceneInstance(Cutscene cutscene, ServerWorld world) {
		this.cutscene = cutscene;
		this.transitions = cutscene.createTransitions();
		this.savedPlayerData = new HashMap<>();
		this.playerDummies = HashBiMap.create();
		setTargetWorld(cutscene.getEntryDim().map(
				dim -> world.getServer().getWorld(dim)
		).orElse(world));
	}

	protected CutsceneInstance(
			Cutscene cutscene, IntervalMap<Transition> transitions, int time, CutsceneWorldData data,
			Map<UUID, NbtCompound> savedPlayerData, Map<UUID, UUID> playerDummies,
			boolean ended, RegistryKey<World> dim
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

	public EntityLookup<Entity> getEntityLookup() {
		return world.getEntityManager().getLookup();
	}

	public boolean hasPlayers() {
		return !players.isEmpty();
	}

	public RegistryKey<World> getDim() {
		return dim;
	}

	public Cutscene getCutscene() {
		return cutscene;
	}

	/**
	 * Updates the cutscenes internal dimension, making sure it does not crash the game.
	 * Running this function after decoding {@link CutsceneInstance#CODEC} is mandatory
	 * (not necessary when using {@link CutsceneInstance#CutsceneInstance(Cutscene, ServerWorld)}).
	 * @param server The minecraft server to get the dimension from.
	 */
	public void finalizeParse(MinecraftServer server) {
		setTargetWorld(server.getWorld(dim));
	}

	public void forAllPlayers(Consumer<ServerPlayerEntity> playerAction) {
		players.forEach(playerAction);
	}

	public Set<ServerPlayerEntity> getPlayers() {
		return players;
	}

	public boolean hasPlayer(ServerPlayerEntity player) {
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
						var source = getServer().getCommandFunctionManager().getScheduledCommandSource();
						getServer().getCommandManager().executeWithPrefix(source, command);
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
	public void setTargetWorld(ServerWorld targetWorld) {
		if (this.world != null && this.world.getActualWorld() == targetWorld) return;
		var prev = this.world;
		this.world = new CutsceneWorld(targetWorld, this, data);
		data = null;
		this.dim = world.getRegistryKey();
		players.forEach(world::addPlayer);
		if (prev != null) {
			world.transferFrom(prev);
		}
	}

	public void setRemoveHandler(RemoveHandler onRemove) {
		this.onRemove = onRemove;
	}

	public Random getRandom() {
		return getCutsceneWorld().getRandom();
	}

	private static NbtCompound writeSafeData(ServerPlayerEntity player) {
		var nbt = player.writeNbt(new NbtCompound());
		nbt.remove(CUTSCENE);
		return nbt;
	}

	public boolean canAddPlayer(ServerPlayerEntity player) {
		return world.isPlayerWorld(player) || getCurrentTarget(player).isPresent();
	}

	public boolean isPlayerHiddenFrom(ServerPlayerEntity player, ServerPlayerEntity target) {
		if (cutscene.hidePlayer() && players.contains(player) && !ended) {
			return true;
		}
		if (getTransitions().getValuesAt(getCurrentTime()).anyMatch(transition -> transition == HideOtherPlayersTransition.getInstance())) {
			return players.contains(target) && players.contains(player);
		}
		return false;
	}

	private boolean isValidTarget(TeleportTarget target) {
		return world.getActualWorld() == target.world();
	}

	private Optional<TeleportTarget> getCurrentTarget(ServerPlayerEntity player) {
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

	public void addPlayerDummy(ServerPlayerEntity player, Function<NbtCompound, Entity> entitySpawner) {
		var existing = playerDummies.get(player.getUuid());
		if (existing != null && getEntityLookup().get(existing) != null) {
			return;
		}
		var entity = entitySpawner.apply(savedPlayerData.get(player.getUuid()));
		if (entity == null) return;
		entity.streamSelfAndPassengers().forEach(e -> {
			e.getCommandTags().add(PLAYER_DUMMY_TAG);
			if (e instanceof PlayerMob) {
				playerDummies.put(player.getUuid(), e.getUuid());
			}
		});
	}

	public Optional<Entity> getPlayerDummyFor(ServerPlayerEntity player) {
		if (playerDummiesAtTheEnd != null) {
			return Optional.of(playerDummiesAtTheEnd.get(player.getUuid()));
		}
		var dummy = playerDummies.get(player.getUuid());
		if (dummy == null) {
			return Optional.empty();
		}
		return Optional.ofNullable(getEntityLookup().get(dummy));
	}

	private void removeLead(ServerPlayerEntity player) {
		player.getServerWorld().getEntitiesByType(
				TypeFilter.instanceOf(Entity.class),
				entity -> entity instanceof Leashable leashable && leashable.isLeashed() && leashable.getLeashHolder() == player
		).forEach(entity -> {
			((Leashable) entity).detachLeashWithoutDrop();
			if (!player.isCreative()) {
				var lead = new ItemStack(Items.LEAD);
				player.getInventory().insertStack(lead);
				if (!lead.isEmpty()) {
					var leadEntity = new ItemEntity(player.getWorld(), player.getX(), player.getY(), player.getZ(), lead);
					leadEntity.setNeverDespawn();
					player.getWorld().spawnEntity(leadEntity);
				}
			}
		});
	}

	public void addPlayer(ServerPlayerEntity player) {
		if (world == null) {
			Cutscenes.LOGGER.error("Attempted to add player to cutscene before world initialized!");
			return;
		}
		removeLead(player);
		if (!savedPlayerData.containsKey(player.getUuid())) {
			PlayerDataHelper.detachPassengersBeforeSaving(player);
			savedPlayerData.put(player.getUuid(), writeSafeData(player));
			PlayerDataHelper.unloadPassengersAndVehicles(player);
			PlayerDataHelper.unloadFarawayEntities(player);
		}
		if (world != null && !world.isPlayerWorld(player)) {
			getCurrentTarget(player).ifPresent(player::teleportTo);
		}
		players = players.plus(player);
		world.addPlayer(player);
		transitions.getIntervalsAt(getCurrentTime()).forEach(interval -> {
			if (interval.getStart() != getCurrentTime()) {
				interval.getObject().activate(player, this, interval);
			}
		});
		world.getActualWorld().getChunkManager().updatePosition(player);


		swapScoreboards(player, world.getActualWorld().getScoreboard(), world.getScoreboard());
	}

	private void swapScoreboards(
			ServerPlayerEntity player, ServerScoreboard old,
			ServerScoreboard newScoreboard
	) {
		if (old == newScoreboard) return;
		for (var team : old.getTeams()) {
			player.networkHandler.sendPacket(TeamS2CPacket.updateRemovedTeam(team));
		}
		for (var slot : ScoreboardDisplaySlot.values()) {
			var ob = old.getObjectiveForSlot(slot);
			if (ob != null) {
				for (var p : old.createRemovePackets(ob)) {
					player.networkHandler.sendPacket(p);
				}
			}
		}
		((AccessorPlayerManager) player.getServer().getPlayerManager()).callSendScoreboard(newScoreboard, player);
	}

	public static void loadPlayerData(
			ServerPlayerEntity player, NbtCompound data, boolean usePlayerDataPosition,
			Optional<TeleportTarget> exitPosOverride
	) {
		PlayerDataHelper.applyPlayerData(player, data, usePlayerDataPosition && exitPosOverride.isEmpty());
		exitPosOverride.ifPresent(teleportTarget -> player.getRootVehicle().teleportTo(teleportTarget));
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

	protected void resetPlayer(ServerPlayerEntity player, boolean isLeavingCutscene) {
		if (cutscene.resetPlayerData()) {
			var data = savedPlayerData.get(player.getUuid());
			if (data == null) {
				data = new NbtCompound();
			}
			if (skipNextCutscene(isLeavingCutscene)) {
				loadPlayerData(player, data, cutscene.returnToStart(), cutscene.getExitPoint(player, this));
			}
		} else if (skipNextCutscene(isLeavingCutscene)) {
			TeleportTarget target = null;
			if (cutscene.hasExitPoint()) {
				target = cutscene.getExitPoint(player, this).orElseThrow();
			} else if (cutscene.returnToStart() && skipNextCutscene(isLeavingCutscene)) {
				target = Optional.ofNullable(savedPlayerData.get(player.getUuid())).flatMap(data -> {
					NbtList pos = data.getList("Pos", NbtCompound.DOUBLE_TYPE);
					NbtList velocity = data.getList("Motion", NbtCompound.DOUBLE_TYPE);
					NbtList rotation = data.getList("Rotation", NbtCompound.FLOAT_TYPE);
					var dim = PlayerDataHelper.getWorld(player.getServer(), data);
					return dim.map(world -> {
						return new TeleportTarget(
								world, new Vec3d(pos.getDouble(0), pos.getDouble(1), pos.getDouble(2)),
								new Vec3d(velocity.getDouble(0), velocity.getDouble(1), velocity.getDouble(2)),
								rotation.getFloat(0), rotation.getFloat(1), TeleportTarget.NO_OP
						);
					});
				}).orElseGet(() -> player.getRespawnTarget(true, TeleportTarget.NO_OP));
			}
			if (target != null) {
				player.teleportTo(target);
			}

			if (savedPlayerData.containsKey(player.getUuid())) {
				var data = savedPlayerData.get(player.getUuid());
				PlayerDataHelper.loadRootVehicleAndPassengers(player, data, e -> {
					if (player.getWorld().spawnEntity(e)) {
						e.updatePositionAndAngles(player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());
						return e;
					}
					return null;
				});
				if (target != null) {
					var vehicle = player.getRootVehicle();
					vehicle.teleportTo(
							target.withPosition(target.position().subtract(player.getPos().subtract(vehicle.getPos())))
					);
				}
				player.readEnderPearls(Optional.of(data));
			}

		}
		if (skipNextCutscene(isLeavingCutscene)) {
			savedPlayerData.remove(player.getUuid());
		}
	}

	public void resetPlayers() {
		forAllPlayers(p -> {
			removePlayer(p, false);
			resetPlayer(p, false);
		});
	}

	protected void disableTransitions(ServerPlayerEntity player) {
		transitions.getIntervalsAt(getCurrentTime()).forEach(interval -> {
			interval.getObject().deactivate(player, this, interval);
		});
	}

	public void removePlayer(ServerPlayerEntity player, boolean isLeavingCutscene) {
		disableTransitions(player);
		players = players.minus(player);
		if (world != null) {
			world.removePlayer(player, isLeavingCutscene);
		}

		if (cutscene.hidePlayer()) {
			var tracker = EntityTrackerHelper.getEntityTrackers(player.getServerWorld()).get(player.getId());
			for (var p : getServer().getPlayerManager().getPlayerList()) {
				if (p != player) {
					tracker.updateTrackedStatus(p);
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
		if (dummyToPlayer.containsKey(entity.getUuid())) {
			var playerID = dummyToPlayer.get(entity.getUuid());
			if (!ended) {
				var player = getServer().getPlayerManager().getPlayer(playerID);
				if (player == null || !players.contains(player)) {
					dummyToPlayer.remove(entity.getUuid());
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
		forAllPlayers(p -> p.networkHandler.sendPacket(packet));
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

	public record QueueEntry(ServerPlayerEntity player, Operation operation) {
		public enum Operation {
			ADD, REMOVE
		}
	}

	public interface RemoveHandler {
		void beforePlayerReset(CutsceneInstance cutscene);
		void afterPlayerReset(CutsceneInstance cutscene);
	}

}
