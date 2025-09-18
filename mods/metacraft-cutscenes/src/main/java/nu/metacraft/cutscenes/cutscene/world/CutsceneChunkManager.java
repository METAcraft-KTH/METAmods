package nu.metacraft.cutscenes.cutscene.world;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.entity.Entity;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.world.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.LightType;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.light.LightStorage;
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

public class CutsceneChunkManager extends ServerChunkManager {

	private final Long2ObjectMap<CutsceneChunk> cachedChunks = new Long2ObjectOpenHashMap<>();

	public final CutsceneChunkLoadingManager cutsceneChunkLoadingManager;

	private final CutsceneWorld cutsceneWorld;

	public CutsceneChunkManager(
			CutsceneWorld cutsceneWorld, Supplier<PersistentStateManager> persistentStateManagerFactory
	) {
		super(
				cutsceneWorld.getActualWorld(),
				((AccessorMinecraftServer) cutsceneWorld.getServer()).getSession(),
				cutsceneWorld.getServer().getDataFixer(),
				cutsceneWorld.getServer().getStructureTemplateManager(),
				((AccessorMinecraftServer) cutsceneWorld.getServer()).getWorkerExecutor(),
				CutsceneWorld.createDummyChunkGenerator(cutsceneWorld.getActualWorld()),
				cutsceneWorld.getServer().getPlayerManager().getViewDistance(),
				cutsceneWorld.getServer().getPlayerManager().getSimulationDistance(),
				cutsceneWorld.getServer().syncChunkWrites(),
				(pos, status) -> {}, persistentStateManagerFactory
		);
		var tickerManager = new ChunkTicketManager();
		this.cutsceneWorld = cutsceneWorld;
		this.cutsceneChunkLoadingManager = new CutsceneChunkLoadingManager(
				cutsceneWorld,
				((AccessorMinecraftServer) cutsceneWorld.getServer()).getSession(),
				cutsceneWorld.getServer().getDataFixer(),
				cutsceneWorld.getServer().getStructureTemplateManager(),
				((AccessorMinecraftServer) cutsceneWorld.getServer()).getWorkerExecutor(),
				((AccessorServerChunkManager) this).getMainThreadExecutor(),
				this, CutsceneWorld.createDummyChunkGenerator(cutsceneWorld.getActualWorld()),
				(pos, status) -> {}, persistentStateManagerFactory,
				tickerManager,
				cutsceneWorld.getServer().getPlayerManager().getViewDistance(),
				cutsceneWorld.getServer().syncChunkWrites()
		);
		((AccessorServerChunkManager) this).setChunkLoadingManager(
				cutsceneChunkLoadingManager
		);
		((AccessorServerChunkManager) this).setTicketManager(
				tickerManager
		);
		((AccessorServerChunkManager) this).setLevelManager(
				cutsceneChunkLoadingManager.getLevelManager()
		);
		((AccessorServerChunkManager) this).setLightingProvider(
				cutsceneChunkLoadingManager.getLightingProvider()
		);
		this.cutsceneChunkLoadingManager.getLevelManager().setSimulationDistance(cutsceneWorld.getServer().getPlayerManager().getSimulationDistance());

	}

	@Override
	public boolean isChunkLoaded(int x, int z) {
		return isInCache(x, z) || cutsceneWorld.getActualWorld().isChunkLoaded(x, z);
	}

	private boolean isInCache(int x, int z) {
		return cachedChunks.containsKey(ChunkPos.toLong(x, z));
	}

	private WorldChunk getFromCache(int x, int z) {
		return cachedChunks.get(ChunkPos.toLong(x, z));
	}

	public ChunkHolder getChunkHolder(int x, int z) {
		return ((CutsceneChunkLoadingManager) chunkLoadingManager).getChunkHolder(
				ChunkPos.toLong(x, z)
		);
	}

	@Override
	public PersistentStateManager getPersistentStateManager() {
		return cutsceneWorld.getPersistentStateManager();
	}

	private boolean fetching = false;

	private Chunk getCutsceneChunk(int x, int z, Chunk chunk) {
		if (chunk instanceof WorldChunk wc && !fetching) {
			fetching = true; //Mob spawners may cause this function to be called recursively.
			var c = cachedChunks.computeIfAbsent(ChunkPos.toLong(x, z), i -> new CutsceneChunk(wc, cutsceneWorld));
			c.setLoadedToWorld(true);
			c.setLightOn(true);
			c.addChunkTickSchedulers(cutsceneWorld);
			var holder = getChunkHolder(x, z);
			((AccessorChunkHolder) holder).setTickingFuture(
					CompletableFuture.completedFuture(OptionalChunk.of(c))
			);
			((AccessorAbstractChunkHolder) holder).setStatus(ChunkStatus.FULL);
			((AccessorAbstractChunkHolder) holder).getCurrentStatus().set(ChunkStatus.FULL);
			var statuses = ((AccessorAbstractChunkHolder) holder).getChunkFuturesByStatus();
			for (int i = 0; i < statuses.length(); i++) {
				statuses.set(i, CompletableFuture.completedFuture(OptionalChunk.of(c)));
			}
			getLightingProvider().initializeLight(c, true);
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
		for (int i = 0; i < cutsceneWorld.countVerticalSections(); i++) {
			int y = cutsceneWorld.sectionIndexToCoord(i);
			long pos = ChunkSectionPos.asLong(x, y, z);
			if (LightingHelper.getBlockLightProvider(getLightingProvider()).getStatus(pos) != LightStorage.Status.LIGHT_AND_DATA) {
				return false;
			}
			if (LightingHelper.getSkyLightProvider(getLightingProvider()).getStatus(pos) != LightStorage.Status.LIGHT_AND_DATA) {
				return false;
			}
		}
		return true;
	}

	public Optional<WorldChunk> getChunkFromCacheIfPresent(int x, int z) {
		return isInCache(x, z) ? Optional.of(getFromCache(x, z)) : Optional.empty();
	}

	public Stream<CutsceneChunk> streamChangedChunks() {
		return cachedChunks.values().stream();
	}

	@Nullable
	@Override
	public Chunk getChunk(int x, int z, ChunkStatus leastStatus, boolean create) {
		if (isInCache(x, z)) {
			return getFromCache(x, z);
		}
		return getCutsceneChunk(x, z, cutsceneWorld.getActualWorld().getChunk(x, z, leastStatus, create));
	}

	@Override
	public WorldChunk getWorldChunk(int chunkX, int chunkZ) {
		if (isInCache(chunkX, chunkZ)) {
			return getFromCache(chunkX, chunkZ);
		}
		return (WorldChunk) getCutsceneChunk(
				chunkX, chunkZ,
				cutsceneWorld.getActualWorld().getChunkManager().getWorldChunk(chunkX, chunkZ)
		);
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
			cachedChunks.values().forEach(chunk -> {
				getChunkHolder(chunk.getPos().x, chunk.getPos().z).flushUpdates(chunk);
			});
		}
	}

	@Override
	public void save(boolean flush) {

	}

	@Override
	public String getDebugString() {
		return cutsceneWorld.getActualWorld().getChunkManager().getDebugString();
	}

	@Override
	public int getLoadedChunkCount() {
		return cutsceneWorld.getActualWorld().getChunkManager().getLoadedChunkCount();
	}

	@Override
	public World getWorld() {//This runs before cutsceneWorld has been initialized properly, so we need to provide a fallback.
		return cutsceneWorld != null ? cutsceneWorld : super.getWorld();
	}

	public CutsceneWorld getCutsceneWorld() {
		return cutsceneWorld;
	}

	@Override
	public void sendToNearbyPlayers(Entity entity, Packet<? super ClientPlayPacketListener> packet) {
		cutsceneWorld.getPlayers().forEach(p -> p.networkHandler.sendPacket(packet));
	}

	@Override
	public void sendToOtherNearbyPlayers(Entity entity, Packet<? super ClientPlayPacketListener> packet) {
		cutsceneWorld.getPlayers().forEach(p -> {
			if (p != entity) {
				p.networkHandler.sendPacket(packet);
			}
		});
	}
}
