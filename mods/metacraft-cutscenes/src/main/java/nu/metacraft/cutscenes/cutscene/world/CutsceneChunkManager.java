package nu.metacraft.cutscenes.cutscene.world;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.jetbrains.annotations.Nullable;
import nu.metacraft.cutscenes.mixin.AccessorAbstractChunkHolder;
import nu.metacraft.cutscenes.mixin.AccessorChunkHolder;
import nu.metacraft.cutscenes.mixin.AccessorMinecraftServer;
import nu.metacraft.cutscenes.mixin.AccessorServerChunkManager;
import nu.metacraft.cutscenes.util.helper.LightingHelper;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class CutsceneChunkManager extends ServerChunkCache {

	private final Long2ObjectMap<CutsceneChunk> cachedChunks = new Long2ObjectOpenHashMap<>();

	public final CutsceneChunkLoadingManager cutsceneChunkLoadingManager;

	private final CutsceneWorld cutsceneWorld;

	public CutsceneChunkManager(
			CutsceneWorld cutsceneWorld, Supplier<DimensionDataStorage> persistentStateManagerFactory
	) {
		super(
				cutsceneWorld.getActualWorld(),
				((AccessorMinecraftServer) cutsceneWorld.getServer()).getStorageSource(),
				cutsceneWorld.getServer().getFixerUpper(),
				cutsceneWorld.getServer().getStructureManager(),
				((AccessorMinecraftServer) cutsceneWorld.getServer()).getExecutor(),
				CutsceneWorld.createDummyChunkGenerator(cutsceneWorld.getActualWorld()),
				cutsceneWorld.getServer().getPlayerList().getViewDistance(),
				cutsceneWorld.getServer().getPlayerList().getSimulationDistance(),
				cutsceneWorld.getServer().forceSynchronousWrites(),
				(pos, status) -> {}, persistentStateManagerFactory
		);
		var tickerManager = new TicketStorage();
		this.cutsceneWorld = cutsceneWorld;
		this.cutsceneChunkLoadingManager = new CutsceneChunkLoadingManager(
				cutsceneWorld,
				((AccessorMinecraftServer) cutsceneWorld.getServer()).getStorageSource(),
				cutsceneWorld.getServer().getFixerUpper(),
				cutsceneWorld.getServer().getStructureManager(),
				((AccessorMinecraftServer) cutsceneWorld.getServer()).getExecutor(),
				((AccessorServerChunkManager) this).getMainThreadExecutor(),
				this, CutsceneWorld.createDummyChunkGenerator(cutsceneWorld.getActualWorld()),
				(pos, status) -> {}, persistentStateManagerFactory,
				tickerManager,
				cutsceneWorld.getServer().getPlayerList().getViewDistance(),
				cutsceneWorld.getServer().forceSynchronousWrites()
		);
		((AccessorServerChunkManager) this).setChunkMap(
				cutsceneChunkLoadingManager
		);
		((AccessorServerChunkManager) this).setTicketStorage(
				tickerManager
		);
		((AccessorServerChunkManager) this).setDistanceManager(
				cutsceneChunkLoadingManager.getDistanceManager()
		);
		((AccessorServerChunkManager) this).setLightEngine(
				cutsceneChunkLoadingManager.getLightEngine()
		);
		this.cutsceneChunkLoadingManager.getDistanceManager().updateSimulationDistance(cutsceneWorld.getServer().getPlayerList().getSimulationDistance());

	}

	@Override
	public boolean hasChunk(int x, int z) {
		return isInCache(x, z) || cutsceneWorld.getActualWorld().hasChunk(x, z);
	}

	private boolean isInCache(int x, int z) {
		return cachedChunks.containsKey(ChunkPos.asLong(x, z));
	}

	private LevelChunk getFromCache(int x, int z) {
		return cachedChunks.get(ChunkPos.asLong(x, z));
	}

	public ChunkHolder getChunkHolder(int x, int z) {
		return ((CutsceneChunkLoadingManager) chunkMap).getVisibleChunkIfPresent(
				ChunkPos.asLong(x, z)
		);
	}

	@Override
	public DimensionDataStorage getDataStorage() {
		return cutsceneWorld.getDataStorage();
	}

	private boolean fetching = false;

	private ChunkAccess getCutsceneChunk(int x, int z, ChunkAccess chunk) {
		if (chunk instanceof LevelChunk wc && !fetching) {
			fetching = true; //Mob spawners may cause this function to be called recursively.
			var c = cachedChunks.computeIfAbsent(ChunkPos.asLong(x, z), i -> new CutsceneChunk(wc, cutsceneWorld));
			c.setLoaded(true);
			c.setLightCorrect(true);
			c.registerTickContainerInLevel(cutsceneWorld);
			var holder = getChunkHolder(x, z);
			((AccessorChunkHolder) holder).setTickingChunkFuture(
					CompletableFuture.completedFuture(ChunkResult.of(c))
			);
			((AccessorAbstractChunkHolder) holder).setHighestAllowedStatus(ChunkStatus.FULL);
			((AccessorAbstractChunkHolder) holder).getStartedWork().set(ChunkStatus.FULL);
			var statuses = ((AccessorAbstractChunkHolder) holder).getFutures();
			for (int i = 0; i < statuses.length(); i++) {
				statuses.set(i, CompletableFuture.completedFuture(ChunkResult.of(c)));
			}
			getLightEngine().initializeLight(c, true);
			if (cutsceneWorld.loaded) {
				c.fetchEntitiesFromActualWorld();
			}
			fetching = false;
			return c;
		}
		return chunk;
	}

	public boolean isLightingCached(int x, int z) {
		if (isInCache(x, z)) return true;
		for (int i = 0; i < cutsceneWorld.getSectionsCount(); i++) {
			int y = cutsceneWorld.getSectionYFromSectionIndex(i);
			long pos = SectionPos.asLong(x, y, z);
			if (LightingHelper.getBlockLightProvider(getLightEngine()).getDebugSectionType(pos) != LayerLightSectionStorage.SectionType.LIGHT_AND_DATA) {
				return false;
			}
			if (LightingHelper.getSkyLightProvider(getLightEngine()).getDebugSectionType(pos) != LayerLightSectionStorage.SectionType.LIGHT_AND_DATA) {
				return false;
			}
		}
		return true;
	}

	public Optional<LevelChunk> getChunkFromCacheIfPresent(int x, int z) {
		return isInCache(x, z) ? Optional.of(getFromCache(x, z)) : Optional.empty();
	}

	public Stream<CutsceneChunk> streamChangedChunks() {
		return cachedChunks.values().stream();
	}

	@Nullable
	@Override
	public ChunkAccess getChunk(int x, int z, ChunkStatus leastStatus, boolean create) {
		if (isInCache(x, z)) {
			return getFromCache(x, z);
		}
		return getCutsceneChunk(x, z, cutsceneWorld.getActualWorld().getChunk(x, z, leastStatus, create));
	}

	@Override
	public LevelChunk getChunkNow(int chunkX, int chunkZ) {
		if (isInCache(chunkX, chunkZ)) {
			return getFromCache(chunkX, chunkZ);
		}
		return (LevelChunk) getCutsceneChunk(
				chunkX, chunkZ,
				cutsceneWorld.getActualWorld().getChunkSource().getChunkNow(chunkX, chunkZ)
		);
	}

	@Override
	public CompletableFuture<ChunkResult<ChunkAccess>> getChunkFuture(int chunkX, int chunkZ, ChunkStatus leastStatus, boolean create) {
		if (isInCache(chunkX, chunkZ)) {
			return CompletableFuture.completedFuture(ChunkResult.of(getFromCache(chunkX, chunkZ)));
		}
		return super.getChunkFuture(chunkX, chunkZ, leastStatus, create).thenApply(
				c -> c.map(chunk -> getCutsceneChunk(chunkX, chunkZ, chunk))
		);
	}

	@Override
	public boolean isPositionTicking(long pos) {
		int x = ChunkPos.getX(pos);
		int z = ChunkPos.getZ(pos);
		if (isInCache(x, z)) {
			return true;
		}
		return super.isPositionTicking(pos);
	}

	@Override
	public void blockChanged(BlockPos pos) {
		getChunkHolder(
				SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ())
		).blockChanged(pos);
	}

	@Override
	public void onLightUpdate(LightLayer type, SectionPos pos) {
		getChunkHolder(pos.getX(), pos.getZ()).sectionLightChanged(type, pos.getY());
	}

	@Override
	public void tick(BooleanSupplier shouldKeepTicking, boolean tickChunks) {
		if (tickChunks) {
			cachedChunks.values().forEach(chunk -> {
				getChunkHolder(chunk.getPos().x, chunk.getPos().z).broadcastChanges(chunk);
			});
		}
	}

	@Override
	public void save(boolean flush) {

	}

	@Override
	public String gatherStats() {
		return cutsceneWorld.getActualWorld().getChunkSource().gatherStats();
	}

	@Override
	public int getLoadedChunksCount() {
		return cutsceneWorld.getActualWorld().getChunkSource().getLoadedChunksCount();
	}

	@Override
	public Level getLevel() {//This runs before cutsceneWorld has been initialized properly, so we need to provide a fallback.
		return cutsceneWorld != null ? cutsceneWorld : super.getLevel();
	}

	public CutsceneWorld getCutsceneWorld() {
		return cutsceneWorld;
	}

	@Override
	public void sendToTrackingPlayersAndSelf(Entity entity, Packet<? super ClientGamePacketListener> packet) {
		cutsceneWorld.players().forEach(p -> p.connection.send(packet));
	}

	@Override
	public void sendToTrackingPlayers(Entity entity, Packet<? super ClientGamePacketListener> packet) {
		cutsceneWorld.players().forEach(p -> {
			if (p != entity) {
				p.connection.send(packet);
			}
		});
	}
}
