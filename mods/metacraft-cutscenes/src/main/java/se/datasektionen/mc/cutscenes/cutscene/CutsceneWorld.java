package se.datasektionen.mc.cutscenes.cutscene;

import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.mojang.datafixers.DataFixer;
import com.mojang.serialization.Dynamic;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet;
import it.unimi.dsi.fastutil.shorts.ShortSet;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.map.MapState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BundleS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkData;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import net.minecraft.recipe.BrewingRecipeRegistry;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.server.WorldGenerationProgressListener;
import net.minecraft.server.network.EntityTrackerEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.*;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.function.LazyIterationConsumer;
import net.minecraft.util.math.*;
import net.minecraft.util.thread.ThreadExecutor;
import net.minecraft.world.*;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.*;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.entity.EntityLookup;
import net.minecraft.world.event.GameEvent;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.FlatChunkGenerator;
import net.minecraft.world.gen.chunk.FlatChunkGeneratorConfig;
import net.minecraft.world.level.LevelProperties;
import net.minecraft.world.level.ServerWorldProperties;
import net.minecraft.world.level.storage.LevelStorage;
import net.minecraft.world.poi.PointOfInterestStorage;
import net.minecraft.world.storage.StorageKey;
import net.minecraft.world.tick.TickManager;
import org.jetbrains.annotations.Nullable;
import se.datasektionen.mc.cutscenes.Cutscenes;
import se.datasektionen.mc.cutscenes.mixin.*;
import se.datasektionen.mc.metacraft_lib.util.helper.EntityTrackerHelper;
import se.datasektionen.mc.metacraft_lib.util.helper.StructureTemplateHelper;
import se.datasektionen.mc.metacraft_lib.util.helper.WorldHelper;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

public class CutsceneWorld extends ServerWorld implements ServerWorldAccess {

	private final ServerWorld world;
	private final CutsceneInstance cutscene;
	private CutsceneChunkManager manager;

	private final EntityLookup<Entity> lookup;

	private PersistentStateManager persistentStateManager;

	private static final Supplier<PersistentStateManager> PERSISTENT_STATE_MANAGER_FACTORY = () -> new PersistentStateManager(
			null, null, null
	) {

		private final Map<String, PersistentState> loadedStates = Maps.newHashMap();

		@Override
		public <T extends PersistentState> T getOrCreate(PersistentState.Type<T> type, String id) {
			return (T) loadedStates.computeIfAbsent(id, i -> type.constructor().get());
		}

		@Override
		public <T extends PersistentState> T get(PersistentState.Type<T> type, String id) {
			return (T) loadedStates.get(id);
		}

		@Override
		public void set(String id, PersistentState state) {
			this.loadedStates.put(id, state);
		}

		@Override
		public NbtCompound readNbt(String id, DataFixTypes dataFixTypes, int currentSaveVersion) throws IOException {
			return new NbtCompound();
		}

		@Override
		public void save() {
			//TODO Save this.
		}
	};

	private static ChunkGenerator createDummyChunkGenerator(World world) {
		return new FlatChunkGenerator(new FlatChunkGeneratorConfig(
				Optional.empty(), world.getBiome(BlockPos.ORIGIN), List.of()
		));
	}

	private static ServerWorldProperties readProperties(
			NbtCompound nbt, ServerWorld parent
	) {
		var p = parent.getServer().getSaveProperties();
		return LevelProperties.readProperties(
				new Dynamic<>(
						NbtOps.INSTANCE,
						nbt
				), p.getLevelInfo(),
				p.isFlatWorld() ? LevelProperties.SpecialProperty.FLAT : p.isDebugWorld() ? LevelProperties.SpecialProperty.DEBUG : LevelProperties.SpecialProperty.NONE,
				p.getGeneratorOptions(), p.getLifecycle()
		);
	}

	private static ServerWorldProperties getProperties(
			ServerWorld parent, CutsceneInstance cutscene
	) {
		return readProperties(
				cutscene.getInitialSavePropertiesData(parent), parent
		);
	}

	protected CutsceneWorld(ServerWorld world, CutsceneInstance cutscene) {
		super(
				world.getServer(), ((AccessorMinecraftServer) world.getServer()).getWorkerExecutor(),
				((AccessorMinecraftServer) world.getServer()).getSession(),
				getProperties(world, cutscene),
				world.getRegistryKey(),
				new DimensionOptions(
						world.getRegistryManager().get(RegistryKeys.DIMENSION_TYPE).getEntry(world.getDimension()),
						createDummyChunkGenerator(world)
				),
				WorldHelper.getGenerationProgressListener(world),
				world.isDebugWorld(), world.getSeed(), List.of(), true, world.getRandomSequences()
		);
		this.savingDisabled = true;
		this.cutscene = cutscene;
		this.world = world;
		this.manager = new CutsceneChunkManager();
		this.lookup = new CombinedEntityLookup(
				List.of(cutscene.getEntityLookup(), ((AccessorServerWorld) world).callGetEntityLookup())
		);
	}

	public CutsceneInstance getCutscene() {
		return cutscene;
	}

	private void sendBlocks(ServerPlayerEntity player, UnaryOperator<Chunk> chunkGetter) {
		List<Packet<? super ClientPlayPacketListener>> list = new ArrayList<>();
		streamChangedChunks().flatMap(c -> {
			Int2ObjectMap<ShortSet> map = new Int2ObjectOpenHashMap<>();
			var chunk = chunkGetter.apply(c);
			c.changedBlocks.forEach(pos -> {
				map.computeIfAbsent(this.getSectionIndex(pos.getY()), i -> new ShortOpenHashSet()).add(
						ChunkSectionPos.packLocal(pos)
				);
			});
			if (chunk == null) return Stream.empty();
			return map.int2ObjectEntrySet().stream().map(entry -> new ChunkDeltaUpdateS2CPacket(
					ChunkSectionPos.from(chunk.getPos(), this.sectionIndexToCoord(entry.getIntKey())),
					entry.getValue(), chunk.getSection(entry.getIntKey())
			));
		}).forEach(list::add);
		if (!list.isEmpty()) {
			if (player.networkHandler != null) {
				player.networkHandler.sendPacket(new BundleS2CPacket(list));
			} else {
				Cutscenes.LOGGER.info("Skipped sending blocks because no network handler :(");
			}
		}
	}

	public void syncTime() {
		cutscene.sendToPlayers(
				new WorldTimeUpdateS2CPacket(
						getTime(), getTimeOfDay(),
						world.getGameRules().getBoolean(GameRules.DO_DAYLIGHT_CYCLE)
				)
		);
	}

	@Override
	public void tick(BooleanSupplier shouldKeepTicking) {
		if (this.getTime() % 20 == 0) {
			syncTime();
		}
		super.tick(shouldKeepTicking);
	}

	public void addPlayer(ServerPlayerEntity player) {
		this.getPlayers().add(player);
		sendBlocks(player, c -> c);
		getChunkManager().cutsceneChunkLoadingManager.addPlayer(player);
	}

	public void removePlayer(ServerPlayerEntity player) {
		this.getPlayers().remove(player);
		sendBlocks(player, c -> world.getChunk(c.getPos().x, c.getPos().z, ChunkStatus.FULL, false));
		getChunkManager().cutsceneChunkLoadingManager.removePlayer(player);
	}

	public boolean isPlayerWorld(PlayerEntity player) {
		return player.getWorld() == world;
	}

	public ServerWorld getActualWorld() {
		return world;
	}

	public Stream<CutsceneChunk> streamChangedChunks() {
		return manager.cachedChunks.values().stream().flatMap(c -> c.values().stream());
	}

	public Stream<BlockPos> streamChangedBlocks() {
		return streamChangedChunks().flatMap(c -> c.changedBlocks.stream());
	}

	public NbtCompound saveLevelProperties() {
		return ((SaveProperties) this.getLevelProperties()).cloneWorldNbt(getRegistryManager(), null);
	}

	public CutsceneInstance.CutsceneWorldData.SerialisedStructure save() {
		StructureTemplate template = new StructureTemplate();
		List<StructureTemplate.StructureBlockInfo> fullBlocks = new ArrayList<>();
		List<StructureTemplate.StructureBlockInfo> blockWithNBT = new ArrayList<>();
		List<StructureTemplate.StructureBlockInfo> otherBlocks = new ArrayList<>();

		BlockPos.Mutable min = new BlockPos.Mutable(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
		BlockPos.Mutable max = new BlockPos.Mutable(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);

		streamChangedChunks().forEach(chunk -> {
			chunk.changedBlocks.forEach(pos -> {
				var newState = chunk.getBlockState(pos);
				var blockEntity = chunk.getBlockEntity(pos);
				StructureTemplate.StructureBlockInfo info = new StructureTemplate.StructureBlockInfo(
						pos, newState, blockEntity != null ? blockEntity.createNbtWithId(getRegistryManager()) : null
				);
				StructureTemplateHelper.categorize(info, fullBlocks, blockWithNBT, otherBlocks);

				if (pos.getX() < min.getX()) {
					min.setX(pos.getX());
				}
				if (pos.getY() < min.getY()) {
					min.setY(pos.getY());
				}
				if (pos.getZ() < min.getZ()) {
					min.setZ(pos.getZ());
				}
				if (pos.getX() > max.getX()) {
					max.setX(pos.getX());
				}
				if (pos.getY() > max.getY()) {
					max.setY(pos.getY());
				}
				if (pos.getZ() > max.getZ()) {
					max.setZ(pos.getZ());
				}
			});
		});
		BlockBox box = BlockBox.create(min, max);
		StructureTemplateHelper.setSize(template, box.getDimensions());
		List<StructureTemplate.StructureBlockInfo> blocks = StructureTemplateHelper.combineSorted(fullBlocks, blockWithNBT, otherBlocks);
		StructureTemplateHelper.getBlockInfoLists(template).add(
				StructureTemplateHelper.createPalettedBlockInfoList(blocks)
		);
		return new CutsceneInstance.CutsceneWorldData.SerialisedStructure(template);
	}

	public void clear() {
		if (!this.getPlayers().isEmpty()) {
			streamChangedBlocks().forEach(pos -> {
				world.getChunkManager().markForUpdate(pos);
			});
		}
	}

	@Override
	public void markDirty(BlockPos pos) {
		if (this.isChunkLoaded(pos)) {
			if (getWorldChunk(pos) instanceof CutsceneChunk chunk) {
				chunk.changedBlocks.add(pos);
			}
		}
	}

	@Override
	public void playSound(@Nullable PlayerEntity source, double x, double y, double z, RegistryEntry<SoundEvent> sound, SoundCategory category, float volume, float pitch, long seed) {
		world.playSound(source, x, y, z, sound, category, volume, pitch, seed);
	}

	@Override
	public void playSoundFromEntity(@Nullable PlayerEntity source, Entity entity, RegistryEntry<SoundEvent> sound, SoundCategory category, float volume, float pitch, long seed) {
		world.playSoundFromEntity(source, entity, sound, category, volume, pitch, seed);
	}

	@Override
	public PersistentStateManager getPersistentStateManager() {
		//ChunkManager is not initialized when this is run for the first time, so we must create it here.
		if (persistentStateManager == null) {
			persistentStateManager = PERSISTENT_STATE_MANAGER_FACTORY.get();
		}
		return persistentStateManager;
	}

	@Override
	public String asString() {
		return world.asString();
	}

	@Nullable
	@Override
	public Entity getEntityById(int id) {
		return getEntityLookup().get(id);
	}

	@Override
	public TickManager getTickManager() {
		return world.getTickManager();
	}

	@Nullable
	@Override
	public MapState getMapState(MapIdComponent id) {
		return world.getMapState(id);
	}

	@Override
	public void putMapState(MapIdComponent id, MapState state) {
		world.putMapState(id, state);
	}

	@Override
	public MapIdComponent increaseAndGetMapId() {
		return world.increaseAndGetMapId();
	}

	@Override
	public void setBlockBreakingInfo(int entityId, BlockPos pos, int progress) {
		world.setBlockBreakingInfo(entityId, pos, progress);
	}

	@Override
	public ServerScoreboard getScoreboard() {
		return world.getScoreboard();
	}

	@Override
	public RecipeManager getRecipeManager() {
		return world.getRecipeManager();
	}

	@Override
	protected EntityLookup<Entity> getEntityLookup() {
		return lookup;
	}

	@Override
	public BrewingRecipeRegistry getBrewingRecipeRegistry() {
		return world.getBrewingRecipeRegistry();
	}

	@Override
	public CutsceneChunkManager getChunkManager() {
		return manager;
	}

	@Override
	public boolean spawnEntity(Entity entity) {
		cutscene.addEntity("AddedByWorld" + entity.getId(), entity);
		return true;
	}

	@Override
	public boolean tryLoadEntity(Entity entity) {
		return spawnEntity(entity);
	}

	@Override
	public void onDimensionChanged(Entity entity) {
		if (entity instanceof ServerPlayerEntity) {
			world.onDimensionChanged(entity);
		} else {
			spawnEntity(entity);
		}
	}

	@Override
	public void onPlayerConnected(ServerPlayerEntity player) {
		world.onPlayerConnected(player);
	}

	@Override
	public void onPlayerRespawned(ServerPlayerEntity player) {
		world.onPlayerRespawned(player);
	}

	@Override
	public void syncWorldEvent(@Nullable PlayerEntity player, int eventId, BlockPos pos, int data) {
		world.syncWorldEvent(player, eventId, pos, data);
	}

	@Override
	public void emitGameEvent(RegistryEntry<GameEvent> event, Vec3d emitterPos, GameEvent.Emitter emitter) {
		world.emitGameEvent(event, emitterPos, emitter);
	}

	@Override
	public float getBrightness(Direction direction, boolean shaded) {
		return world.getBrightness(direction, shaded);
	}

	@Override
	public RegistryEntry<Biome> getGeneratorStoredBiome(int biomeX, int biomeY, int biomeZ) {
		return world.getGeneratorStoredBiome(biomeX, biomeY, biomeZ);
	}

	@Override
	public FeatureSet getEnabledFeatures() {
		return world.getEnabledFeatures();
	}

	@Override
	public ServerWorld toServerWorld() {
		return this;
	}

	public class CutsceneChunkManager extends ServerChunkManager {

		private final Int2ObjectMap<Int2ObjectMap<ChunkHolder>> cachedChunkHolders = new Int2ObjectOpenHashMap<>();
		private final Int2ObjectMap<Int2ObjectMap<CutsceneChunk>> cachedChunks = new Int2ObjectOpenHashMap<>();

		public final CutsceneChunkLoadingManager cutsceneChunkLoadingManager;

		public CutsceneChunkManager() {
			super(
					world, ((AccessorMinecraftServer) world.getServer()).getSession(), world.getServer().getDataFixer(),
					world.getServer().getStructureTemplateManager(), ((AccessorMinecraftServer) world.getServer()).getWorkerExecutor(),
					createDummyChunkGenerator(world), world.getServer().getPlayerManager().getViewDistance(),
					world.getServer().getPlayerManager().getSimulationDistance(), world.getServer().syncChunkWrites(),
					WorldHelper.getGenerationProgressListener(world),
					(pos, status) -> {}, PERSISTENT_STATE_MANAGER_FACTORY
			);
			this.cutsceneChunkLoadingManager = new CutsceneChunkLoadingManager(
					world, ((AccessorMinecraftServer) world.getServer()).getSession(), world.getServer().getDataFixer(),
					world.getServer().getStructureTemplateManager(),
					((AccessorMinecraftServer) world.getServer()).getWorkerExecutor(), ((AccessorServerChunkManager) this).getMainThreadExecutor(),
					this, createDummyChunkGenerator(world), WorldHelper.getGenerationProgressListener(world),
					(pos, status) -> {}, PERSISTENT_STATE_MANAGER_FACTORY,
					world.getServer().getPlayerManager().getViewDistance(), world.getServer().syncChunkWrites()
			);
			((AccessorServerChunkManager) this).setChunkLoadingManager(
					cutsceneChunkLoadingManager
			);
		}

		@Override
		public boolean isChunkLoaded(int x, int z) {
			return isInCache(x, z) || world.isChunkLoaded(x, z);
		}

		private boolean isInCache(int x, int z) {
			return cachedChunks.containsKey(x) && cachedChunks.get(x).containsKey(z);
		}

		private Chunk getFromCache(int x, int z) {
			return cachedChunks.get(x).get(z);
		}

		private ChunkHolder getChunkHolder(int x, int z) {
			if (cachedChunkHolders.containsKey(x) && cachedChunkHolders.get(x).containsKey(z)) {
				return cachedChunkHolders.get(x).get(z);
			}

			var holderCol = cachedChunkHolders.computeIfAbsent(x, i -> new Int2ObjectOpenHashMap<>());
			return holderCol.computeIfAbsent(z, i -> new ChunkHolder(
					new ChunkPos(x, z), ChunkLevels.getLevelFromType(ChunkLevelType.ENTITY_TICKING),
					CutsceneWorld.this, CutsceneWorld.this.getLightingProvider(),
					(a, b, c, d) -> {}, (p, b) -> getPlayers()
			));
		}

		@Override
		public PersistentStateManager getPersistentStateManager() {
			return CutsceneWorld.this.getPersistentStateManager();
		}

		private Chunk getCutsceneChunk(int x, int z, Chunk chunk) {
			if (chunk instanceof WorldChunk wc) {
				var col = cachedChunks.computeIfAbsent(x, i -> new Int2ObjectOpenHashMap<>());
				var c = col.computeIfAbsent(z, i -> new CutsceneChunk(wc, world));
				((AccessorChunkHolder) getChunkHolder(x, z)).setTickingFuture(
						CompletableFuture.completedFuture(OptionalChunk.of(c))
				);
				return c;
			}
			return chunk;
		}

		@Nullable
		@Override
		public Chunk getChunk(int x, int z, ChunkStatus leastStatus, boolean create) {
			if (isInCache(x, z)) {
				return getFromCache(x, z);
			}
			return getCutsceneChunk(x, z, world.getChunk(x, z, leastStatus, create));
		}

		@Override
		public WorldChunk getWorldChunk(int chunkX, int chunkZ) {
			if (isInCache(chunkX, chunkZ)) {
				return (WorldChunk) getFromCache(chunkX, chunkZ);
			}
			return (WorldChunk) getCutsceneChunk(chunkX, chunkZ, world.getChunkManager().getWorldChunk(chunkX, chunkZ));
		}

		@Override
		public CompletableFuture<OptionalChunk<Chunk>> getChunkFutureSyncOnMainThread(int chunkX, int chunkZ, ChunkStatus leastStatus, boolean create) {
			if (isInCache(chunkX, chunkZ)) {
				return CompletableFuture.completedFuture(OptionalChunk.of(getFromCache(chunkX, chunkZ)));
			}
			return super.getChunkFutureSyncOnMainThread(chunkX, chunkZ, leastStatus, create).thenApply(
					c -> c.map(chunk -> getCutsceneChunk(chunkX, chunkZ, chunk))
			);
		}

		@Override
		public boolean isTickingFutureReady(long pos) {
			int x = ChunkPos.getPackedX(pos);
			int z = ChunkPos.getPackedZ(pos);
			if (isInCache(x, z)) {
				return true;
			}
			return super.isTickingFutureReady(pos);
		}

		@Override
		public void markForUpdate(BlockPos pos) {
			getChunkHolder(
					ChunkSectionPos.getSectionCoord(pos.getX()), ChunkSectionPos.getSectionCoord(pos.getZ())
			).markForBlockUpdate(pos);
		}

		@Override
		public void onLightUpdate(LightType type, ChunkSectionPos pos) {
			getChunkHolder(pos.getX(), pos.getZ()).markForLightUpdate(type, pos.getY());
		}

		@Override
		public void tick(BooleanSupplier shouldKeepTicking, boolean tickChunks) {
			if (tickChunks) {
				cachedChunks.values().stream().flatMap(c -> c.values().stream()).forEach(chunk -> {
					getChunkHolder(chunk.getPos().x, chunk.getPos().z).flushUpdates(chunk);
				});
			}
		}

		@Override
		public void save(boolean flush) {

		}

		@Override
		public String getDebugString() {
			return world.getChunkManager().getDebugString();
		}

		@Override
		public int getLoadedChunkCount() {
			return world.getChunkManager().getLoadedChunkCount();
		}

		@Override
		public World getWorld() {
			return CutsceneWorld.this;
		}

		@Override
		public void sendToNearbyPlayers(Entity entity, Packet<?> packet) {
			getPlayers().forEach(p -> p.networkHandler.sendPacket(packet));
		}

		@Override
		public void sendToOtherNearbyPlayers(Entity entity, Packet<?> packet) {
			getPlayers().forEach(p -> {
				if (p != entity) {
					p.networkHandler.sendPacket(packet);
				}
			});
		}
	}

	public class CutsceneChunkLoadingManager extends ServerChunkLoadingManager {
		public CutsceneChunkLoadingManager(
				ServerWorld world, LevelStorage.Session session,
				DataFixer dataFixer, StructureTemplateManager structureTemplateManager,
				Executor executor, ThreadExecutor<Runnable> mainThreadExecutor,
				ChunkProvider chunkProvider, ChunkGenerator chunkGenerator,
				WorldGenerationProgressListener worldGenerationProgressListener,
				ChunkStatusChangeListener chunkStatusChangeListener,
				Supplier<PersistentStateManager> persistentStateManagerFactory,
				int viewDistance, boolean dsync
		) {
			super(world, session, dataFixer, structureTemplateManager, executor, mainThreadExecutor, chunkProvider, chunkGenerator, worldGenerationProgressListener, chunkStatusChangeListener, persistentStateManagerFactory, viewDistance, dsync);
			((AccessorServerChunkLoadingManager) this).setPointOfInterestStorage(
					new PointOfInterestStorage(
							new StorageKey(session.getDirectoryName(), world.getRegistryKey(), "poi"),
							session.getWorldDirectory(world.getRegistryKey()).resolve("poi"), dataFixer, dsync,
							world.getRegistryManager(), world.getServer(), world
					) { //TODO Save this.

						@Override
						public void saveChunk(ChunkPos pos) {

						}

						@Override
						public boolean hasUnsavedElements() {
							return false;
						}

					}
			);
		}

		public void addPlayer(ServerPlayerEntity player) {
			EntityTrackerHelper.getEntityTrackers(this).values().forEach(t -> {
				EntityTrackerHelper.getListeners(t).add(player.networkHandler);
			});
		}

		public void removePlayer(ServerPlayerEntity player) {
			EntityTrackerHelper.getEntityTrackers(this).values().forEach(t -> {
				EntityTrackerHelper.getListeners(t).remove(player.networkHandler);
			});
		}

		public void addEntity(Entity entity, EntityTrackerEntry entry) {
			var e = new EntityTracker(entity, 0, 0, false) {

				@Override
				public void sendToNearbyPlayers(Packet<?> packet) {
					sendToOtherNearbyPlayers(packet);
				}

				@Override
				public void updateTrackedStatus(ServerPlayerEntity player) {
					var listeners = EntityTrackerHelper.getListeners(this);
					if (cutscene.hasPlayer(player)) {
						if (listeners.add(player.networkHandler)) {
							entry.startTracking(player);
						}
					} else if (listeners.remove(player.networkHandler)) {
						entry.stopTracking(player);
					}
				}
			};
			((AccessorServerChunkLoadingManager.EntityTracker) (Object) e).setEntry(entry);
			CutsceneWorld.this.getPlayers().forEach(p -> {
				EntityTrackerHelper.getListeners(e).add(p.networkHandler);
			});
			EntityTrackerHelper.getEntityTrackers(this).put(
				entity.getId(), e
			);
		}

		public void removeEntity(Entity entity) {
			EntityTrackerHelper.getEntityTrackers(this).remove(entity.getId());
		}
	}

	public static class CutsceneChunk extends WorldChunk {

		private final Set<BlockPos> changedBlocks = new HashSet<>();

		public CutsceneChunk(WorldChunk chunk, World world) {
			super(world, chunk.getPos());
			var data = new ChunkData(chunk);
			this.loadFromPacket(data.getSectionsDataBuf(), data.getHeightmap(), data.getBlockEntities(chunk.getPos().x, chunk.getPos().z));
			this.setLevelTypeProvider(chunk::getLevelType);
		}

		@Override
		public BlockState setBlockState(BlockPos pos, BlockState state, boolean moved) {
			changedBlocks.add(pos);
			return super.setBlockState(pos, state, moved);
		}

		@Override
		public void setBlockEntity(BlockEntity blockEntity) {
			changedBlocks.add(blockEntity.getPos());
			super.setBlockEntity(blockEntity);
		}

		@Override
		public void removeBlockEntity(BlockPos pos) {
			changedBlocks.add(pos);
			super.removeBlockEntity(pos);
		}

		@Override
		public void clear() {
			changedBlocks.clear();
			super.clear();
		}
	}

	public static class CombinedEntityLookup implements EntityLookup<Entity> {

		private final List<EntityLookup<Entity>> lookups;

		public CombinedEntityLookup(List<EntityLookup<Entity>> lookups) {
			this.lookups = lookups;
		}

		@Nullable
		@Override
		public Entity get(int id) {
			return lookups.stream().flatMap(lookup -> Optional.ofNullable(lookup.get(id)).stream()).findFirst().orElse(null);
		}

		@Nullable
		@Override
		public Entity get(UUID uuid) {
			return lookups.stream().flatMap(lookup -> Optional.ofNullable(lookup.get(uuid)).stream()).findFirst().orElse(null);
		}

		@Override
		public Iterable<Entity> iterate() {
			return Iterables.concat((Iterable<Iterable<Entity>>) () -> lookups.stream().map(EntityLookup::iterate).iterator());
		}

		@Override
		public <U extends Entity> void forEach(TypeFilter<Entity, U> filter, LazyIterationConsumer<U> consumer) {
			lookups.forEach(lookup -> lookup.forEach(filter, consumer));
		}

		@Override
		public void forEachIntersects(Box box, Consumer<Entity> action) {
			lookups.forEach(lookup -> lookup.forEachIntersects(box, action));
		}

		@Override
		public <U extends Entity> void forEachIntersects(TypeFilter<Entity, U> filter, Box box, LazyIterationConsumer<U> consumer) {
			lookups.forEach(lookup -> lookup.forEachIntersects(filter, box, consumer));
		}
	}
}
