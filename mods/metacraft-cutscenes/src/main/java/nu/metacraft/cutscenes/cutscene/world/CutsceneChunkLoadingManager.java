package nu.metacraft.cutscenes.cutscene.world;

import com.mojang.datafixers.DataFixer;
import net.minecraft.entity.Entity;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.EntityTrackerEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.*;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.thread.ThreadExecutor;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.chunk.ChunkProvider;
import net.minecraft.world.chunk.ChunkStatusChangeListener;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.level.storage.LevelStorage;
import net.minecraft.world.poi.PointOfInterestStorage;
import net.minecraft.world.storage.StorageKey;
import org.jetbrains.annotations.Nullable;
import nu.metacraft.cutscenes.mixin.AccessorServerChunkLoadingManager;
import nu.metacraft.cutscenes.mixin.AccessorServerLightingProvider;
import nu.metacraft.lib.util.helper.EntityTrackerHelper;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

public class CutsceneChunkLoadingManager extends ServerChunkLoadingManager {

	private final CutsceneWorld cutsceneWorld;

	private final Map<Long,ChunkHolder> cachedChunkHolders = new ConcurrentHashMap<>();

	public CutsceneChunkLoadingManager(
			CutsceneWorld cutsceneWorld, LevelStorage.Session session,
			DataFixer dataFixer, StructureTemplateManager structureTemplateManager,
			Executor executor, ThreadExecutor<Runnable> mainThreadExecutor,
			ChunkProvider chunkProvider, ChunkGenerator chunkGenerator,
			ChunkStatusChangeListener chunkStatusChangeListener,
			Supplier<PersistentStateManager> persistentStateManagerFactory,
			ChunkTicketManager ticketManager,
			int viewDistance, boolean dsync
	) {
		super(cutsceneWorld.getActualWorld(), session, dataFixer, structureTemplateManager, executor, mainThreadExecutor, chunkProvider, chunkGenerator, chunkStatusChangeListener, persistentStateManagerFactory, ticketManager, viewDistance, dsync);
		this.cutsceneWorld = cutsceneWorld;
		((AccessorServerChunkLoadingManager) this).setLightingProvider(
				new CutsceneLightingProvider(
						chunkProvider, this,
						cutsceneWorld.getDimension().hasSkyLight(),
						((AccessorServerLightingProvider) getLightingProvider()).getProcessor(),
						((AccessorServerChunkLoadingManager) this).getLightScheduler()
				)
		);
		((AccessorServerChunkLoadingManager) this).setLevelManager(
				new LevelManager(ticketManager, mainThreadExecutor, executor) {

					@Override
					protected ChunkHolder setLevel(long pos, int level, @Nullable ChunkHolder holder, int i) {
						return holder;
					}
				}
		);
		((AccessorServerChunkLoadingManager) this).setPointOfInterestStorage(
				new PointOfInterestStorage(
						new StorageKey(session.getDirectoryName(), cutsceneWorld.getRegistryKey(), "poi"),
						session.getWorldDirectory(cutsceneWorld.getRegistryKey()).resolve("poi"), dataFixer, dsync,
						cutsceneWorld.getRegistryManager(), cutsceneWorld.getServer(), cutsceneWorld.getActualWorld()
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

	@Override
	public ServerLightingProvider getLightingProvider() {
		return super.getLightingProvider();
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
			public void sendToSelfAndListeners(Packet<? super ClientPlayPacketListener> packet) {
				sendToListeners(packet);
			}

			@Override
			public void updateTrackedStatus(ServerPlayerEntity player) {
				var listeners = EntityTrackerHelper.getListeners(this);
				if (cutsceneWorld.getCutscene().hasPlayer(player)) {
					if (listeners.add(player.networkHandler)) {
						entry.startTracking(player);
					}
				} else if (listeners.remove(player.networkHandler)) {
					entry.stopTracking(player);
				}
			}
		};
		((AccessorServerChunkLoadingManager.EntityTracker) (Object) e).setEntry(entry);
		cutsceneWorld.getPlayers().forEach(p -> {
			EntityTrackerHelper.getListeners(e).add(p.networkHandler);
		});
		EntityTrackerHelper.getEntityTrackers(this).put(
				entity.getId(), e
		);
	}

	public void removeEntity(Entity entity) {
		EntityTrackerHelper.getEntityTrackers(this).remove(entity.getId());
	}

	public CutsceneWorld getCutsceneWorld() {
		return cutsceneWorld;
	}

	@Override
	public ChunkHolder getCurrentChunkHolder(long pos) {
		return getChunkHolder(pos);
	}

	@Override
	public ChunkHolder getChunkHolder(long pos) {
		if (cachedChunkHolders.containsKey(pos)) {
			return cachedChunkHolders.get(pos);
		}

		return cachedChunkHolders.computeIfAbsent(pos, i -> new ChunkHolder(
				new ChunkPos(pos), ChunkLevels.getLevelFromType(ChunkLevelType.ENTITY_TICKING),
				cutsceneWorld, getLightingProvider(),
				(a, b, c, d) -> {},
				(p, b) -> cutsceneWorld.getPlayers()
		) {
			@Override
			protected void updateStatus(ServerChunkLoadingManager chunkLoadingManager) {

			}

			@Override
			protected void updateFutures(ServerChunkLoadingManager chunkLoadingManager, Executor executor) {

			}
		});
	}

	@Override
	protected void save(boolean flush) {

	}
}
