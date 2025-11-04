package nu.metacraft.cutscenes.cutscene.world;

import com.mojang.datafixers.DataFixer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.entity.ChunkStatusUpdateListener;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.jetbrains.annotations.Nullable;
import nu.metacraft.cutscenes.mixin.ChunkMapAccessor;
import nu.metacraft.cutscenes.mixin.ThreadedLevelLightEngineAccessor;
import nu.metacraft.lib.util.helper.EntityTrackerHelper;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

public class CutsceneChunkLoadingManager extends ChunkMap {

	private final CutsceneWorld cutsceneWorld;

	private final Map<Long,ChunkHolder> cachedChunkHolders = new ConcurrentHashMap<>();

	public CutsceneChunkLoadingManager(
			CutsceneWorld cutsceneWorld, LevelStorageSource.LevelStorageAccess session,
			DataFixer dataFixer, StructureTemplateManager structureTemplateManager,
			Executor executor, BlockableEventLoop<Runnable> mainThreadExecutor,
			LightChunkGetter chunkProvider, ChunkGenerator chunkGenerator,
			ChunkStatusUpdateListener chunkStatusChangeListener,
			Supplier<DimensionDataStorage> persistentStateManagerFactory,
			TicketStorage ticketManager,
			int viewDistance, boolean dsync
	) {
		super(cutsceneWorld.getActualWorld(), session, dataFixer, structureTemplateManager, executor, mainThreadExecutor, chunkProvider, chunkGenerator, chunkStatusChangeListener, persistentStateManagerFactory, ticketManager, viewDistance, dsync);
		this.cutsceneWorld = cutsceneWorld;
		((ChunkMapAccessor) this).setLightEngine(
				new CutsceneLightingProvider(
						chunkProvider, this,
						cutsceneWorld.dimensionType().hasSkyLight(),
						((ThreadedLevelLightEngineAccessor) getLightEngine()).getConsecutiveExecutor(),
						((ChunkMapAccessor) this).getLightTaskDispatcher()
				)
		);
		((ChunkMapAccessor) this).setDistanceManager(
				new DistanceManager(ticketManager, mainThreadExecutor, executor) {

					@Override
					protected ChunkHolder updateChunkScheduling(long pos, int level, @Nullable ChunkHolder holder, int i) {
						return holder;
					}
				}
		);
		((ChunkMapAccessor) this).setPoiManager(
				new PoiManager(
						new RegionStorageInfo(session.getLevelId(), cutsceneWorld.dimension(), "poi"),
						session.getDimensionPath(cutsceneWorld.dimension()).resolve("poi"), dataFixer, dsync,
						cutsceneWorld.registryAccess(), cutsceneWorld.getServer(), cutsceneWorld.getActualWorld()
				) { //TODO Save this.

					@Override
					public void flush(ChunkPos pos) {

					}

					@Override
					public boolean hasWork() {
						return false;
					}

				}
		);
	}

	@Override
	public ThreadedLevelLightEngine getLightEngine() {
		return super.getLightEngine();
	}

	public void addPlayer(ServerPlayer player) {
		EntityTrackerHelper.getEntityTrackers(this).values().forEach(t -> {
			EntityTrackerHelper.getListeners(t).add(player.connection);
		});
	}

	public void removePlayer(ServerPlayer player) {
		EntityTrackerHelper.getEntityTrackers(this).values().forEach(t -> {
			EntityTrackerHelper.getListeners(t).remove(player.connection);
		});
	}

	public void addEntity(Entity entity, ServerEntity entry) {
		var e = new TrackedEntity(entity, 0, 0, false) {

			@Override
			public void sendToTrackingPlayersAndSelf(Packet<? super ClientGamePacketListener> packet) {
				sendToTrackingPlayers(packet);
			}

			@Override
			public void updatePlayer(ServerPlayer player) {
				var listeners = EntityTrackerHelper.getListeners(this);
				if (cutsceneWorld.getCutscene().hasPlayer(player)) {
					if (listeners.add(player.connection)) {
						entry.addPairing(player);
					}
				} else if (listeners.remove(player.connection)) {
					entry.removePairing(player);
				}
			}
		};
		((ChunkMapAccessor.TrackedEntity) (Object) e).setServerEntity(entry);
		cutsceneWorld.players().forEach(p -> {
			EntityTrackerHelper.getListeners(e).add(p.connection);
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
	public ChunkHolder getUpdatingChunkIfPresent(long pos) {
		return getVisibleChunkIfPresent(pos);
	}

	@Override
	public ChunkHolder getVisibleChunkIfPresent(long pos) {
		if (cachedChunkHolders.containsKey(pos)) {
			return cachedChunkHolders.get(pos);
		}

		return cachedChunkHolders.computeIfAbsent(pos, i -> new ChunkHolder(
				new ChunkPos(pos), ChunkLevel.byStatus(FullChunkStatus.ENTITY_TICKING),
				cutsceneWorld, getLightEngine(),
				(a, b, c, d) -> {},
				(p, b) -> cutsceneWorld.players()
		) {
			@Override
			protected void updateHighestAllowedStatus(ChunkMap chunkLoadingManager) {

			}

			@Override
			protected void updateFutures(ChunkMap chunkLoadingManager, Executor executor) {

			}
		});
	}

	@Override
	protected void saveAllChunks(boolean flush) {

	}
}
