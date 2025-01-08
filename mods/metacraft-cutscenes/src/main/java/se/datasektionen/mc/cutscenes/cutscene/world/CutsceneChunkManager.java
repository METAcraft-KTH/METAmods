package se.datasektionen.mc.cutscenes.cutscene.world;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.entity.Entity;
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
import org.jetbrains.annotations.Nullable;
import se.datasektionen.mc.cutscenes.mixin.AccessorAbstractChunkHolder;
import se.datasektionen.mc.cutscenes.mixin.AccessorChunkHolder;
import se.datasektionen.mc.cutscenes.mixin.AccessorMinecraftServer;
import se.datasektionen.mc.cutscenes.mixin.AccessorServerChunkManager;
import se.datasektionen.mc.metacraft_lib.util.helper.WorldHelper;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class CutsceneChunkManager extends ServerChunkManager {

	private final Int2ObjectMap<Int2ObjectMap<ChunkHolder>> cachedChunkHolders = new Int2ObjectOpenHashMap<>();
	private final Int2ObjectMap<Int2ObjectMap<CutsceneChunk>> cachedChunks = new Int2ObjectOpenHashMap<>();

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
				WorldHelper.getGenerationProgressListener(cutsceneWorld.getActualWorld()),
				(pos, status) -> {}, persistentStateManagerFactory
		);
		this.cutsceneWorld = cutsceneWorld;
		this.cutsceneChunkLoadingManager = new CutsceneChunkLoadingManager(
				cutsceneWorld,
				((AccessorMinecraftServer) cutsceneWorld.getServer()).getSession(),
				cutsceneWorld.getServer().getDataFixer(),
				cutsceneWorld.getServer().getStructureTemplateManager(),
				((AccessorMinecraftServer) cutsceneWorld.getServer()).getWorkerExecutor(),
				((AccessorServerChunkManager) this).getMainThreadExecutor(),
				this, CutsceneWorld.createDummyChunkGenerator(cutsceneWorld.getActualWorld()),
				WorldHelper.getGenerationProgressListener(cutsceneWorld.getActualWorld()),
				(pos, status) -> {}, persistentStateManagerFactory,
				cutsceneWorld.getServer().getPlayerManager().getViewDistance(),
				cutsceneWorld.getServer().syncChunkWrites()
		);
		((AccessorServerChunkManager) this).setChunkLoadingManager(
				cutsceneChunkLoadingManager
		);
		((AccessorServerChunkManager) this).setTicketManager(
				cutsceneChunkLoadingManager.getTicketManager()
		);
		((AccessorServerChunkManager) this).setLightingProvider(
				cutsceneChunkLoadingManager.getLightingProvider()
		);
		this.cutsceneChunkLoadingManager.getTicketManager().setSimulationDistance(cutsceneWorld.getServer().getPlayerManager().getSimulationDistance());

	}

	@Override
	public boolean isChunkLoaded(int x, int z) {
		return isInCache(x, z) || cutsceneWorld.getActualWorld().isChunkLoaded(x, z);
	}

	private boolean isInCache(int x, int z) {
		return cachedChunks.containsKey(x) && cachedChunks.get(x).containsKey(z);
	}

	private WorldChunk getFromCache(int x, int z) {
		return cachedChunks.get(x).get(z);
	}

	public ChunkHolder getChunkHolder(int x, int z) {
		if (cachedChunkHolders.containsKey(x) && cachedChunkHolders.get(x).containsKey(z)) {
			return cachedChunkHolders.get(x).get(z);
		}

		var holderCol = cachedChunkHolders.computeIfAbsent(x, i -> new Int2ObjectOpenHashMap<>());
		return holderCol.computeIfAbsent(z, i -> new ChunkHolder(
				new ChunkPos(x, z), ChunkLevels.getLevelFromType(ChunkLevelType.ENTITY_TICKING),
				cutsceneWorld, getLightingProvider(),
				(a, b, c, d) -> {},
				(p, b) -> cutsceneWorld.getPlayers()
		));
	}

	@Override
	public PersistentStateManager getPersistentStateManager() {
		return cutsceneWorld.getPersistentStateManager();
	}

	private boolean fetching = false;

	private Chunk getCutsceneChunk(int x, int z, Chunk chunk) {
		if (chunk instanceof WorldChunk wc && !fetching) {
			fetching = true; //Mob spawners may cause this function to be called recursively.
			var col = cachedChunks.computeIfAbsent(x, i -> new Int2ObjectOpenHashMap<>());
			var c = col.computeIfAbsent(z, i -> new CutsceneChunk(wc, cutsceneWorld));
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
			getLightingProvider().setColumnEnabled(c.getPos(), true);
			fetching = false;
			return c;
		}
		return chunk;
	}

	public Optional<WorldChunk> getChunkFromCacheIfPresent(int x, int z) {
		return isInCache(x, z) ? Optional.of(getFromCache(x, z)) : Optional.empty();
	}

	public Stream<CutsceneChunk> streamChangedChunks() {
		return cachedChunks.values().stream().flatMap(c -> c.values().stream());
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

	@Override
	public void sendToNearbyPlayers(Entity entity, Packet<?> packet) {
		cutsceneWorld.getPlayers().forEach(p -> p.networkHandler.sendPacket(packet));
	}

	@Override
	public void sendToOtherNearbyPlayers(Entity entity, Packet<?> packet) {
		cutsceneWorld.getPlayers().forEach(p -> {
			if (p != entity) {
				p.networkHandler.sendPacket(packet);
			}
		});
	}
}
