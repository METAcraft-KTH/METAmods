package nu.metacraft.dungeons.dungeons;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.attribute.EnvironmentAttributeReader;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.ticks.LevelTickAccess;
import net.minecraft.world.ticks.ScheduledTick;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class WorldCache implements WorldGenLevel {

	private final ServerLevel world;
	private final Map<BlockPos, BlockEntry> blockCache = new LinkedHashMap<>();
	private final Map<BlockPos, BlockEntity> blockEntities = new HashMap<>();

	private final LevelTickAccess<Block> blockScheduler;
	private final LevelTickAccess<Fluid> fluidScheduler;

	private final Set<Entity> entities = new LinkedHashSet<>();

	public WorldCache(ServerLevel world) {
		this.world = world;
		this.blockScheduler = new DeferredTickScheduler<>(world::getBlockTicks);
		this.fluidScheduler = new DeferredTickScheduler<>(world::getFluidTicks);
	}

	public void flush(long maxTime) {
		CountDownLatch done = new CountDownLatch(1);
		world.getServer().execute(() -> {
			long startTime = System.currentTimeMillis();
			var it = blockCache.entrySet().iterator();
			while (it.hasNext()){
				var entry = it.next();
				if (!world.hasChunkAt(entry.getKey())) {
					break;
				}
				entry.getValue().functionCall.run();
				if (blockEntities.containsKey(entry.getKey())) {
					world.removeBlockEntity(entry.getKey());
					world.setBlockEntity(blockEntities.get(entry.getKey()));
					blockEntities.remove(entry.getKey());
				}
				it.remove();
				if (System.currentTimeMillis() - startTime > maxTime) {
					break;
				}
			}

			var entityIt = entities.iterator();
			while (entityIt.hasNext()) {
				var entity = entityIt.next();
				if (!world.hasChunkAt(entity.blockPosition())) {
					break;
				}
				world.addFreshEntity(entity);
				entityIt.remove();

				if (System.currentTimeMillis() - startTime > maxTime) {
					break;
				}
			}


			done.countDown();
		});
		try {
			done.await();
		} catch (InterruptedException ignored) {}
	}

	public boolean isEmpty() {
		return blockCache.isEmpty();
	}

	@Override
	public long getSeed() {
		return world.getSeed();
	}

	@Override
	public ServerLevel getLevel() {
		return world;
	}

	@Override
	public long nextSubTickCount() {
		return 0;
	}

	@Override
	public LevelTickAccess<Block> getBlockTicks() {
		return blockScheduler;
	}

	@Override
	public LevelTickAccess<Fluid> getFluidTicks() {
		return fluidScheduler;
	}

	@Override
	public void updateNeighborsAt(BlockPos pos, Block block) {
		if (blockCache.containsKey(pos)) {
			blockCache.put(pos, blockCache.get(pos).andThen(() -> {
				world.updateNeighborsAt(pos, block);
			}));
		}
	}

	@Override
	public LevelData getLevelData() {
		return world.getLevelData();
	}

	@Override
	public DifficultyInstance getCurrentDifficultyAt(BlockPos pos) {
		return world.getCurrentDifficultyAt(pos);
	}

	@Nullable
	@Override
	public MinecraftServer getServer() {
		return world.getServer();
	}

	@Override
	public ChunkSource getChunkSource() {
		return world.getChunkSource();
	}

	@Override
	public RandomSource getRandom() {
		return world.getRandom();
	}

	@Override
	public void playSound(@Nullable Entity source, BlockPos pos, SoundEvent sound, SoundSource category, float volume, float pitch) {
		world.playSound(source, pos, sound, category, volume, pitch);
	}

	@Override
	public void addParticle(ParticleOptions parameters, double x, double y, double z, double velocityX, double velocityY, double velocityZ) {
		world.addParticle(parameters, x, y, z, velocityX, velocityY, velocityZ);
	}

	@Override
	public void levelEvent(@Nullable Entity player, int eventId, BlockPos pos, int data) {
		world.levelEvent(player, eventId, pos, data);
	}

	@Override
	public void gameEvent(Holder<GameEvent> event, Vec3 emitterPos, GameEvent.Context emitter) {
		world.gameEvent(event, emitterPos, emitter);
	}

	@Override
	public LevelLightEngine getLightEngine() {
		return world.getLightEngine();
	}

	@Override
	public WorldBorder getWorldBorder() {
		return world.getWorldBorder();
	}

	@Nullable
	@Override
	public BlockEntity getBlockEntity(BlockPos pos) {
		if (blockEntities.containsKey(pos)) {
			var entity = blockEntities.get(pos);
			if (entity.getBlockState() == blockCache.get(pos).state()) {
				return entity;
			}
		}
		if (blockCache.containsKey(pos)) {
			var state = blockCache.get(pos).state();
			if (state.hasBlockEntity()) {
				var blockEntity = ((EntityBlock) state.getBlock()).newBlockEntity(pos.immutable(), state);
				blockEntities.put(pos.immutable(), blockEntity);
				return blockEntity;
			}
		}
		if (hasChunkAt(pos)) {
			return world.getBlockEntity(pos);
		} else {
			return null;
		}
	}

	@Override
	public boolean hasChunk(int chunkX, int chunkZ) {
		return false;
	}

	@Override
	public BlockState getBlockState(BlockPos pos) {
		if (blockCache.containsKey(pos)) {
			return blockCache.get(pos).state();
		}
		if (hasChunkAt(pos)) {
			return world.getBlockState(pos);
		} else {
			return Blocks.AIR.defaultBlockState();
		}
	}

	@Override
	public FluidState getFluidState(BlockPos pos) {
		if (blockCache.containsKey(pos)) {
			return blockCache.get(pos).state().getFluidState();
		}
		if (hasChunkAt(pos)) {
			return world.getFluidState(pos);
		} else {
			return Fluids.EMPTY.defaultFluidState();
		}
	}

	@Override
	public List<Entity> getEntities(@Nullable Entity except, AABB box, Predicate<? super Entity> predicate) {
		return world.getEntities(except, box, predicate);
	}

	@Override
	public boolean addFreshEntity(Entity entity) {
		return entities.add(entity);
	}

	@Override
	public <T extends Entity> List<T> getEntities(EntityTypeTest<Entity, T> filter, AABB box, Predicate<? super T> predicate) {
		return world.getEntities(filter, box, predicate);
	}

	@Override
	public List<? extends Player> players() {
		return world.players();
	}

	@Override
	public boolean setBlock(BlockPos pos, BlockState state, int flags, int maxUpdateDepth) {
		var actualPos = pos.immutable();
		var entry = new BlockEntry(state, () -> {
			world.setBlock(actualPos, state, flags, maxUpdateDepth);
		});
		return !entry.equals(blockCache.put(pos.immutable(), entry));
	}

	@Override
	public boolean removeBlock(BlockPos pos, boolean move) {
		var actualPos = pos.immutable();
		var entry = new BlockEntry(
				getBlockState(pos).getFluidState().createLegacyBlock(),
				() -> world.removeBlock(actualPos, move)
		);
		return !entry.equals(blockCache.put(pos.immutable(), entry));
	}

	@Override
	public boolean destroyBlock(BlockPos pos, boolean drop, @Nullable Entity breakingEntity, int maxUpdateDepth) {
		var actualPos = pos.immutable();
		var entry = new BlockEntry(
				getBlockState(pos).getFluidState().createLegacyBlock(),
				() -> world.destroyBlock(actualPos, drop, breakingEntity, maxUpdateDepth)
		);
		return !entry.equals(blockCache.put(actualPos, entry));
	}

	@Override
	public boolean isStateAtPosition(BlockPos pos, Predicate<BlockState> state) {
		if (blockCache.containsKey(pos)) {
			return state.test(blockCache.get(pos).state());
		}
		if (hasChunkAt(pos)) {
			return world.isStateAtPosition(pos, state);
		} else {
			return state.test(Blocks.AIR.defaultBlockState());
		}
	}

	@Override
	public boolean isFluidAtPosition(BlockPos pos, Predicate<FluidState> state) {
		if (blockCache.containsKey(pos)) {
			return state.test(blockCache.get(pos).state().getFluidState());
		}
		if (hasChunkAt(pos)) {
			return world.isFluidAtPosition(pos, state);
		} else {
			return state.test(Fluids.EMPTY.defaultFluidState());
		}
	}

	@Nullable
	@Override
	public ChunkAccess getChunk(int chunkX, int chunkZ, ChunkStatus leastStatus, boolean create) {
		return world.getChunk(chunkX, chunkZ, leastStatus, create);
	}

	@Override
	public int getHeight(Heightmap.Types heightmap, int x, int z) {
		return world.getHeight(heightmap, x, z);
	}

	@Override
	public int getSkyDarken() {
		return world.getSkyDarken();
	}

	@Override
	public BiomeManager getBiomeManager() {
		return world.getBiomeManager();
	}

	@Override
	public Holder<Biome> getUncachedNoiseBiome(int biomeX, int biomeY, int biomeZ) {
		return world.getUncachedNoiseBiome(biomeX, biomeY, biomeZ);
	}

	@Override
	public boolean isClientSide() {
		return world.isClientSide();
	}

	@Override
	public int getSeaLevel() {
		return world.getSeaLevel();
	}

	@Override
	public DimensionType dimensionType() {
		return world.dimensionType();
	}

	@Override
	public RegistryAccess registryAccess() {
		return world.registryAccess();
	}

	@Override
	public FeatureFlagSet enabledFeatures() {
		return world.enabledFeatures();
	}

	@Override
	public EnvironmentAttributeReader environmentAttributes() {
		return world.environmentAttributes();
	}

	public record BlockEntry(BlockState state, Runnable functionCall) {

		public BlockEntry andThen(Runnable runnable) {
			return new BlockEntry(state, () -> {
				functionCall.run();
				runnable.run();
			});
		}

	}

	public class DeferredTickScheduler<T> implements LevelTickAccess<T> {

		private final Supplier<LevelTickAccess<T>> scheduler;

		public DeferredTickScheduler(Supplier<LevelTickAccess<T>> scheduler) {
			this.scheduler = scheduler;
		}

		@Override
		public boolean willTickThisTick(BlockPos pos, T type) {
			return false;
		}

		@Override
		public void schedule(ScheduledTick<T> orderedTick) {
			if (blockCache.containsKey(orderedTick.pos())) {
				blockCache.put(orderedTick.pos(), blockCache.get(orderedTick.pos()).andThen(() -> {
					scheduler.get().schedule(
						new ScheduledTick<>(orderedTick.type(), orderedTick.pos(), orderedTick.triggerTick(), world.nextSubTickCount())
					);
				}));
			}
		}

		@Override
		public boolean hasScheduledTick(BlockPos pos, T type) {
			return false;
		}

		@Override
		public int count() {
			return 0;
		}
	}
}
