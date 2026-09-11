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
import net.minecraft.world.level.storage.SavedDataStorage;
import org.jetbrains.annotations.Nullable;
import nu.metacraft.cutscenes.mixin.GenerationChunkHolderAccessor;
import nu.metacraft.cutscenes.mixin.ChunkHolderAccessor;
import nu.metacraft.cutscenes.mixin.MinecraftServerAccessor;
import nu.metacraft.cutscenes.mixin.ServerChunkCacheAccessor;
import nu.metacraft.cutscenes.util.helper.LightingHelper;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class CutsceneChunkCache extends ServerChunkCache {

	private final Long2ObjectMap<CutsceneChunk> cachedChunks = new Long2ObjectOpenHashMap<>();

	public final CutsceneChunkLoadingManager cutsceneChunkLoadingManager;

	private final CutsceneLevel cutsceneLevel;

	public CutsceneChunkCache(
			CutsceneLevel cutsceneLevel
	) {
		super(
				cutsceneLevel.getActualWorld(),
				((MinecraftServerAccessor) cutsceneLevel.getServer()).getStorageSource(),
				cutsceneLevel.getServer().getFixerUpper(),
				cutsceneLevel.getServer().getStructureTemplateManager(),
				((MinecraftServerAccessor) cutsceneLevel.getServer()).getExecutor(),
				CutsceneLevel.createDummyChunkGenerator(cutsceneLevel.getActualWorld()),
				cutsceneLevel.getServer().getPlayerList().getViewDistance(),
				cutsceneLevel.getServer().getPlayerList().getSimulationDistance(),
				cutsceneLevel.getServer().forceSynchronousWrites(),
				(pos, status) -> {}
		);
		var tickerManager = new TicketStorage();
		this.cutsceneLevel = cutsceneLevel;
		this.cutsceneChunkLoadingManager = new CutsceneChunkLoadingManager(
				cutsceneLevel,
				((MinecraftServerAccessor) cutsceneLevel.getServer()).getStorageSource(),
				cutsceneLevel.getServer().getFixerUpper(),
				cutsceneLevel.getServer().getStructureTemplateManager(),
				((MinecraftServerAccessor) cutsceneLevel.getServer()).getExecutor(),
				((ServerChunkCacheAccessor) this).getMainThreadExecutor(),
				this, CutsceneLevel.createDummyChunkGenerator(cutsceneLevel.getActualWorld()),
				(pos, status) -> {},
				tickerManager,
				cutsceneLevel.getServer().getPlayerList().getViewDistance(),
				cutsceneLevel.getServer().forceSynchronousWrites()
		);
		((ServerChunkCacheAccessor) this).setChunkMap(
				cutsceneChunkLoadingManager
		);
		((ServerChunkCacheAccessor) this).setTicketStorage(
				tickerManager
		);
		((ServerChunkCacheAccessor) this).setDistanceManager(
				cutsceneChunkLoadingManager.getDistanceManager()
		);
		((ServerChunkCacheAccessor) this).setLightEngine(
				cutsceneChunkLoadingManager.getLightEngine()
		);
		this.cutsceneChunkLoadingManager.getDistanceManager().updateSimulationDistance(cutsceneLevel.getServer().getPlayerList().getSimulationDistance());

	}

	@Override
	public boolean hasChunk(int x, int z) {
		return isInCache(x, z) || cutsceneLevel.getActualWorld().hasChunk(x, z);
	}

	private boolean isInCache(int x, int z) {
		return cachedChunks.containsKey(ChunkPos.pack(x, z));
	}

	private LevelChunk getFromCache(int x, int z) {
		return cachedChunks.get(ChunkPos.pack(x, z));
	}

	public ChunkHolder getChunkHolder(int x, int z) {
		return ((CutsceneChunkLoadingManager) chunkMap).getVisibleChunkIfPresent(
				ChunkPos.pack(x, z)
		);
	}

	@Override
	public SavedDataStorage getDataStorage() {
		return cutsceneLevel.getDataStorage();
	}

	private boolean fetching = false;

	private ChunkAccess getCutsceneChunk(int x, int z, ChunkAccess chunk) {
		if (chunk instanceof LevelChunk wc && !fetching) {
			fetching = true; //Mob spawners may cause this function to be called recursively.
			var c = cachedChunks.computeIfAbsent(ChunkPos.pack(x, z), i -> new CutsceneChunk(wc, cutsceneLevel));
			c.setLoaded(true);
			c.setLightCorrect(true);
			c.registerTickContainerInLevel(cutsceneLevel);
			var holder = getChunkHolder(x, z);
			((ChunkHolderAccessor) holder).setTickingChunkFuture(
					CompletableFuture.completedFuture(ChunkResult.of(c))
			);
			((GenerationChunkHolderAccessor) holder).setHighestAllowedStatus(ChunkStatus.FULL);
			((GenerationChunkHolderAccessor) holder).getStartedWork().set(ChunkStatus.FULL);
			var statuses = ((GenerationChunkHolderAccessor) holder).getFutures();
			for (int i = 0; i < statuses.length(); i++) {
				statuses.set(i, CompletableFuture.completedFuture(ChunkResult.of(c)));
			}
			getLightEngine().initializeLight(c, true);
			if (cutsceneLevel.loaded) {
				c.fetchEntitiesFromActualWorld();
			}
			fetching = false;
			return c;
		}
		return chunk;
	}

	public boolean isLightingCached(int x, int z) {
		if (isInCache(x, z)) return true;
		for (int i = 0; i < cutsceneLevel.getSectionsCount(); i++) {
			int y = cutsceneLevel.getSectionYFromSectionIndex(i);
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
		return getCutsceneChunk(x, z, cutsceneLevel.getActualWorld().getChunk(x, z, leastStatus, create));
	}

	@Override
	public LevelChunk getChunkNow(int chunkX, int chunkZ) {
		if (isInCache(chunkX, chunkZ)) {
			return getFromCache(chunkX, chunkZ);
		}
		return (LevelChunk) getCutsceneChunk(
				chunkX, chunkZ,
				cutsceneLevel.getActualWorld().getChunkSource().getChunkNow(chunkX, chunkZ)
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
				getChunkHolder(chunk.getPos().x(), chunk.getPos().z()).broadcastChanges(chunk);
			});
		}
	}

	@Override
	public void save(boolean flush) {

	}

	@Override
	public String gatherStats() {
		return cutsceneLevel.getActualWorld().getChunkSource().gatherStats();
	}

	@Override
	public int getLoadedChunksCount() {
		return cutsceneLevel.getActualWorld().getChunkSource().getLoadedChunksCount();
	}

	@Override
	public Level getLevel() {//This runs before cutsceneWorld has been initialized properly, so we need to provide a fallback.
		return cutsceneLevel != null ? cutsceneLevel : super.getLevel();
	}

	public CutsceneLevel getCutsceneWorld() {
		return cutsceneLevel;
	}

	@Override
	public void sendToTrackingPlayersAndSelf(Entity entity, Packet<? super ClientGamePacketListener> packet) {
		cutsceneLevel.players().forEach(p -> p.connection.send(packet));
	}

	@Override
	public void sendToTrackingPlayers(Entity entity, Packet<? super ClientGamePacketListener> packet) {
		cutsceneLevel.players().forEach(p -> {
			if (p != entity) {
				p.connection.send(packet);
			}
		});
	}
}
