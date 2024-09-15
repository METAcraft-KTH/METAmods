package se.datasektionen.mc.cutscenes.cutscene;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.*;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.entity.EntityLookup;
import se.datasektionen.mc.cutscenes.Cutscenes;
import se.datasektionen.mc.cutscenes.transitions.DeltaTickTransition;
import se.datasektionen.mc.cutscenes.util.IntervalMap;
import se.datasektionen.mc.cutscenes.mixin.AccessorServerPlayerEntity;
import se.datasektionen.mc.cutscenes.registry.TransitionRegistry;
import se.datasektionen.mc.cutscenes.transitions.TeleportTransition;
import se.datasektionen.mc.cutscenes.transitions.Transition;
import se.datasektionen.mc.cutscenes.transitions.entity.SpawnEntity;
import se.datasektionen.mc.metacraft_core.entity.METAcraftEntities;
import se.datasektionen.mc.metacraft_lib.util.helper.EntityTrackerHelper;

import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class CutsceneInstance implements AutoCloseable {

	private static Timer timer;

	public static final String CUTSCENE = "cutscene";

	public static final String PLAYER_REFERENCE = "player";
	public static final String PLAYER_ITEM = "player_item";

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
					CutsceneWorldData.CODEC.fieldOf("data").forGetter(CutsceneInstance::save),
					SAVED_DATA_CODEC.fieldOf("saved_players").forGetter(cutscene -> cutscene.savedPlayerData),
					Codec.BOOL.fieldOf("ended").forGetter(CutsceneInstance::isEnded),
					World.CODEC.fieldOf("dim").forGetter(CutsceneInstance::getDim)
			).apply(instance, CutsceneInstance::new)
	);

	private final Cutscene cutscene;
	private final IntervalMap<Transition> transitions;
	private int time = 0;
	private boolean ended = false;
	private CutsceneWorld world;
	private final CutsceneEntityManager entities = new CutsceneEntityManager();
	private CutsceneWorldData data;
	private StructureTemplate blocks;
	private int chunkWaitTime = 10;
	private RegistryKey<World> dim;

	private final Map<UUID, NbtCompound> savedPlayerData;

	private final ReadWriteLock lock = new ReentrantReadWriteLock();
	private final Set<ServerPlayerEntity> players = new HashSet<>();
	private final List<QueueEntry> modificationQueue = new ArrayList<>();

	public CutsceneInstance(Cutscene cutscene, ServerWorld world) {
		this.cutscene = cutscene;
		this.transitions = cutscene.createTransitions();
		this.savedPlayerData = new HashMap<>();
		setTargetWorld(world);
	}

	protected CutsceneInstance(
			Cutscene cutscene, IntervalMap<Transition> transitions, int time, CutsceneWorldData data,
			Map<UUID, NbtCompound> savedPlayerData, boolean ended, RegistryKey<World> dim
	) {
		this.cutscene = cutscene;
		this.transitions = transitions;
		this.time = time;
		this.data = data;
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
		return entities.getLookup();
	}

	public boolean hasPlayers() {
		lock.readLock().lock();
		try {
			return !players.isEmpty();
		} finally {
			lock.readLock().unlock();
		}
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

	//Avoid giving direct access to player set from outside since some transitions may access it from another thread.
	public void forAllPlayers(Consumer<ServerPlayerEntity> playerAction) {
		lock.readLock().lock();
		try {
			players.forEach(playerAction);
		} finally {
			lock.readLock().unlock();
		}
	}

	//Avoid giving direct access to player set from outside since some transitions may access it from another thread.
	public Set<ServerPlayerEntity> copyPlayers() {
		lock.readLock().lock();
		try {
			return new HashSet<>(players);
		} finally {
			lock.readLock().unlock();
		}
	}

	public void end() {
		ended = true;
		entities.clear();
		world.clear();
	}

	/**
	 * Changes the dimension used by the cutscene.
	 * If fired during a cutscene, all players should be teleported to the given dimension.
	 * Has no effect if the cutscene is already in the given world.
	 * @param targetWorld The world the cutscene should play in.
	 */
	public void setTargetWorld(ServerWorld targetWorld) {
		if (this.world != null && this.world.getActualWorld() == targetWorld) return;
		this.world = new CutsceneWorld(targetWorld, this);
		this.dim = world.getRegistryKey();
		entities.setWorld(world);
		players.forEach(world::addPlayer);
		entities.clear();
	}

	private void handleQueue() {
		if (!modificationQueue.isEmpty()) {
			lock.writeLock().lock();
			try {
				modificationQueue.forEach(action -> {
					switch (action.operation) {
						case REMOVE -> players.remove(action.player);
						case ADD -> players.add(action.player);
					}
				});
			} finally {
				lock.writeLock().unlock();
			}
			modificationQueue.clear();
		}
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
		return world.isPlayerWorld(player) || getCurrentTarget().isPresent();
	}

	private boolean isValidTarget(TeleportTarget target) {
		return world.getActualWorld() == target.world();
	}

	private Optional<TeleportTarget> getCurrentTarget() {
		var transitionTarget = getTransitions().getValuesAt(getCurrentTime()).map(
				t -> t instanceof TeleportTransition tp ? tp.getTarget(this).filter(this::isValidTarget).orElse(null) : null
		).filter(
				Objects::nonNull
		).findAny();
		if (transitionTarget.isEmpty()) {
			return cutscene.getEntryPoint(world.getServer(), world.getRegistryKey()).filter(this::isValidTarget);
		}
		return transitionTarget;
	}

	public void addPlayer(ServerPlayerEntity player) {
		boolean playerAddedFirstTime = false;
		if (!savedPlayerData.containsKey(player.getUuid())) {
			playerAddedFirstTime = true;
			player.removeAllPassengers();
			var directVehicle = player.getVehicle();
			if (!cutscene.hideMount() && !cutscene.createFakePlayer()) {
				player.dismountVehicle();
			}
			savedPlayerData.put(player.getUuid(), writeSafeData(player));
			if (cutscene.hideMount() || cutscene.createFakePlayer()) {
				var vehicle = player.getRootVehicle();
				player.dismountVehicle();
				if (vehicle != player) {
					vehicle.streamPassengersAndSelf().forEach(Entity::discard);
				}
			} else if (directVehicle != null) {
				player.startRiding(directVehicle, true);
			}
		}
		if (world != null && !world.isPlayerWorld(player)) {
			getCurrentTarget().ifPresent(player::teleportTo);
		}
		modificationQueue.add(new QueueEntry(player, QueueEntry.Operation.ADD));
		if (world != null) {
			world.addPlayer(player);
		}
		entities.addPlayer(player);
		if (playerAddedFirstTime && cutscene.createFakePlayer()) {
			createFromData(savedPlayerData.get(player.getUuid())).ifPresent(p -> {
				p.streamSelfAndPassengers().forEach(e -> {
					entities.addEntity(PLAYER_REFERENCE, e);
				});
			});
		}
		transitions.getIntervalsAt(getCurrentTime()).forEach(interval -> {
			if (interval.getStart() != getCurrentTime()) {
				interval.getObject().activate(player, this, interval);
			}
		});
		if (cutscene.hidePlayer()) {
			var playerPacket = new PlayerRemoveS2CPacket(List.of(player.getUuid()));
			var entityPacket = new EntitiesDestroyS2CPacket(player.getId());
			for (var p : getServer().getPlayerManager().getPlayerList()) {
				if (!players.contains(p) && player != p) {
					p.networkHandler.sendPacket(playerPacket);
					if (p.getWorld() == player.getWorld()) {
						p.networkHandler.sendPacket(entityPacket);
					}
				}
			}
		}
	}

	public Optional<Entity> createFromData(NbtCompound data) {
		var player = METAcraftEntities.PLAYER.create(world);
		var spawnWorld = getWorld(world.getServer(), data);
		if (spawnWorld.isEmpty() || spawnWorld.get() != world.getActualWorld()) {
			return Optional.empty();
		}
		player.copyFromPlayerData(data);
		loadRootVehicle(player, data, e -> {});
		return Optional.of(player.getRootVehicle());
	}

	public static Optional<ServerWorld> getWorld(MinecraftServer server, NbtCompound data) {
		return DimensionType.worldFromDimensionNbt(
				new Dynamic<>(NbtOps.INSTANCE, data.get("Dimension"))
		).flatMap(key -> {
			var dim = server.getWorld(key);
			if (dim == null) {
				return DataResult.error(() -> "Dimension " + key + " did not exist.");
			}
			return DataResult.success(dim);
		}).resultOrPartial(Cutscenes.LOGGER::error);
	}

	public static void loadRootVehicle(LivingEntity player, NbtCompound data, Consumer<Entity> spawner) {
		if (data.contains("RootVehicle")) {
			var vehicle = data.getCompound("RootVehicle");
			var e = EntityType.loadEntityWithPassengers(vehicle.getCompound("Entity"), player.getWorld(), entity -> {
				spawner.accept(entity);
				return entity;
			});
			if (e != null) {
				Runnable clearEntity = () -> {
					e.streamPassengersAndSelf().forEach(Entity::discard);
					Cutscenes.LOGGER.error("Unable to reattach player to entity.");
				};
				if (vehicle.containsUuid("Attach")) {
					var id = vehicle.getUuid("Attach");
					for (var entity : (Iterable<Entity>) e.streamSelfAndPassengers()::iterator) {
						if (entity.getUuid().equals(id)) {
							player.startRiding(entity, true);
						}
					}
					if (!player.hasVehicle()) {
						clearEntity.run();
					}
				} else {
					clearEntity.run();
				}
			}
		}
	}

	public static void loadPlayerData(ServerPlayerEntity player, NbtCompound data, boolean changePosition) {
		var prevPos = player.getPos();
		var prevYaw = player.getYaw();
		var prevPitch = player.getPitch();
		Vec3d prevVelocity = player.getVelocity();
		player.readNbt(data);
		Optional<ServerWorld> world = getWorld(player.getServer(), data);
		if (changePosition) {
			world.ifPresentOrElse(w -> {
				player.teleport(w, player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());
				player.velocityModified = true;
			}, () -> {
				player.teleportTo(player.getRespawnTarget(true, TeleportTarget.NO_OP));
			});
		} else {
			player.setPos(prevPos.getX(), prevPos.getY(), prevPos.getZ());
			player.setYaw(prevYaw);
			player.setPitch(prevPitch);
			player.setVelocity(prevVelocity);
		}
		var gameMode = AccessorServerPlayerEntity.callGameModeFromNbt(data, "playerGameType");
		if (gameMode != null) {
			player.changeGameMode(gameMode);
		}
		loadRootVehicle(player, data, player.getWorld()::spawnEntity);
	}

	public void removeCutscene(Consumer<ServerPlayerEntity> playerAction) {
		resetPlayers(playerAction);
		lock.writeLock().lock();
		try {
			players.clear();
		} finally {
			lock.writeLock().unlock();
		}
	}

	protected void resetPlayer(ServerPlayerEntity player) {
		if (!savedPlayerData.containsKey(player.getUuid())) return;
		if (cutscene.resetPlayerData()) {
			var data = savedPlayerData.remove(player.getUuid());
			loadPlayerData(player, data, cutscene.returnToStart());
		} else if (cutscene.returnToStart()) {
			var data = savedPlayerData.remove(player.getUuid());
			NbtList pos = data.getList("Pos", NbtCompound.DOUBLE_TYPE);
			NbtList velocity = data.getList("Motion", NbtCompound.DOUBLE_TYPE);
			NbtList rotation = data.getList("Rotation", NbtCompound.FLOAT_TYPE);
			var dim = getWorld(player.getServer(), data);
			dim.ifPresentOrElse(world -> {
				player.teleport(
						world, pos.getDouble(0), pos.getDouble(1), pos.getDouble(2),
						rotation.getFloat(0), rotation.getFloat(1)
				);
				player.setVelocity(new Vec3d(
						velocity.getDouble(0), velocity.getDouble(1), velocity.getDouble(2)
				));
				player.velocityModified = true;
			}, () -> {
				player.teleportTo(player.getRespawnTarget(true, TeleportTarget.NO_OP));
			});
		}
	}

	public void resetPlayers(Consumer<ServerPlayerEntity> playerAction) {
		forAllPlayers(p -> {
			playerAction.accept(p);
			resetPlayer(p);
		});
	}

	public void removePlayer(ServerPlayerEntity player) {
		transitions.getIntervalsAt(getCurrentTime()).forEach(interval -> {
			interval.getObject().deactivate(player, this, interval);
		});
		modificationQueue.add(new QueueEntry(player, QueueEntry.Operation.REMOVE));
		if (world != null) {
			world.removePlayer(player);
		}
		entities.removePlayer(player);

		if (cutscene.hidePlayer()) {
			var packet = PlayerListS2CPacket.entryFromPlayer(List.of(player));
			var tracker = EntityTrackerHelper.getEntityTrackers(player.getServerWorld()).get(player.getId());
			for (var p : getServer().getPlayerManager().getPlayerList()) {
				if (!players.contains(p)) {
					p.networkHandler.sendPacket(packet);
					tracker.updateTrackedStatus(p);
				}
			}
		}
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

	public boolean shouldTick() {
		return hasPlayers();
	}

	public void tick() {
		handleQueue();
		if (ended) {
			return;
		}
		if (!shouldTick()) return;
		if (world == null) {
			Cutscenes.LOGGER.error("Cutscene did not have a world, ending it prematurely! If you get this error, some developer forgot to call CutsceneInstance#setTargetWorld or CutsceneInstance#setWorldFromDim");
			end();
		}
		if (data != null) {
			if (blocks == null) {
				blocks = data.blocks.parse(world.getRegistryManager());
			}

			if (chunkWaitTime > 0) {
				chunkWaitTime--;
				return;
			}

			blocks.place(
					world, BlockPos.ORIGIN, BlockPos.ORIGIN, new StructurePlacementData(),
					world.getRandom(), Block.NOTIFY_ALL
			);

			data.entities().forEach(entity -> {
				entity.load(this);
			});
			data = null;
			blocks = null;
			chunkWaitTime = 0;
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
		entities.tick();
		world.tick(() -> true);
		if (time > transitions.getEnd()) {
			end();
		}
	}

	public void addEntity(String id, Entity entity) {
		entities.addEntity(id, entity);
	}

	public Optional<Entity> getRootEntity(String id) {
		return entities.getRootEntity(id);
	}

	public Stream<Entity> getEntities(String id) {
		return entities.getEntities(id);
	}

	public Optional<String> getIDForEntity(Entity entity) {
		return entities.getIDForEntity(entity);
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

	public CutsceneWorldData save() {
		return new CutsceneWorldData(
				entities.save().toList(), world.save()
		);
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

	public record CutsceneWorldData(List<SerialisedEntity> entities, SerialisedStructure blocks) {
		public static final Codec<CutsceneWorldData> CODEC = RecordCodecBuilder.create(
				instance -> instance.group(
						SerialisedEntity.CODEC.listOf().fieldOf("entities").forGetter(d -> d.entities),
						SerialisedStructure.CODEC.fieldOf("blocks").forGetter(d -> d.blocks)
				).apply(instance, CutsceneWorldData::new)
		);

		public record SerialisedEntity(List<String> ids, NbtCompound data) {
			public static final Codec<SerialisedEntity> CODEC = RecordCodecBuilder.create(
					instance -> instance.group(
							Codec.STRING.listOf().fieldOf("ids").forGetter(SerialisedEntity::ids),
							NbtCompound.CODEC.fieldOf("data").forGetter(SerialisedEntity::data)
					).apply(instance, SerialisedEntity::new)
			);

			public void load(CutsceneInstance cutscene) {
				SpawnEntity.spawnEntities(
						ids, data, Optional.empty(), cutscene, Optional.of(false)
				);
			}
		}

		public record SerialisedStructure(NbtCompound data) {
			public static final Codec<SerialisedStructure> CODEC = NbtCompound.CODEC.xmap(
					SerialisedStructure::new, SerialisedStructure::data
			);

			public SerialisedStructure(StructureTemplate structure) {
				this(structure.writeNbt(new NbtCompound()));
			}

			private static NbtList convert(int[] array) {
				var list = new NbtList();
				for (int i : array) {
					list.add(NbtInt.of(i));
				}
				return list;
			}

			public StructureTemplate parse(RegistryWrapper.WrapperLookup lookup) {
				StructureTemplate template = new StructureTemplate();
				//StructureTemplate#readNbt only accepts an int list, but codecs sometimes like to replace it with an int array.
				if (data.contains(StructureTemplate.SIZE_KEY, NbtElement.INT_ARRAY_TYPE)) {
					data.put(StructureTemplate.SIZE_KEY, convert(data.getIntArray(StructureTemplate.SIZE_KEY)));
				}
				if (data.contains(StructureTemplate.BLOCKS_KEY, NbtElement.LIST_TYPE)) {
					var blocks = data.getList(StructureTemplate.BLOCKS_KEY, NbtElement.COMPOUND_TYPE);
					for (var b : blocks) {
						var block = ((NbtCompound) b);
						if (block.contains(StructureTemplate.BLOCKS_POS_KEY, NbtElement.INT_ARRAY_TYPE)) {
							block.put(StructureTemplate.BLOCKS_POS_KEY, convert(block.getIntArray(StructureTemplate.BLOCKS_POS_KEY)));
						}
					}
				}
				if (data.contains(StructureTemplate.ENTITIES_KEY, NbtElement.LIST_TYPE)) {
					var entities = data.getList(StructureTemplate.ENTITIES_KEY, NbtElement.COMPOUND_TYPE);
					for (var e : entities) {
						var entity = ((NbtCompound) e);
						if (entity.contains(StructureTemplate.ENTITIES_BLOCK_POS_KEY, NbtElement.LIST_TYPE)) {
							entity.put(StructureTemplate.ENTITIES_BLOCK_POS_KEY, convert(entity.getIntArray(StructureTemplate.ENTITIES_BLOCK_POS_KEY)));
						}
					}
				}
				template.readNbt(lookup.getWrapperOrThrow(RegistryKeys.BLOCK), data);
				return template;
			}
		}
	}

}
