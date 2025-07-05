package nu.metacraft.dungeons.dungeons;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.WorldProperties;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkManager;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.light.LightingProvider;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.event.GameEvent;
import net.minecraft.world.tick.OrderedTick;
import net.minecraft.world.tick.QueryableTickScheduler;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class WorldCache implements StructureWorldAccess {

	private final ServerWorld world;
	private final Map<BlockPos, BlockEntry> blockCache = new LinkedHashMap<>();
	private final Map<BlockPos, BlockEntity> blockEntities = new HashMap<>();

	private final QueryableTickScheduler<Block> blockScheduler;
	private final QueryableTickScheduler<Fluid> fluidScheduler;

	private final Set<Entity> entities = new LinkedHashSet<>();

	public WorldCache(ServerWorld world) {
		this.world = world;
		this.blockScheduler = new DeferredTickScheduler<>(world::getBlockTickScheduler);
		this.fluidScheduler = new DeferredTickScheduler<>(world::getFluidTickScheduler);
	}

	public void flush(long maxTime) {
		CountDownLatch done = new CountDownLatch(1);
		world.getServer().execute(() -> {
			long startTime = System.currentTimeMillis();
			var it = blockCache.entrySet().iterator();
			while (it.hasNext()){
				var entry = it.next();
				if (!world.isChunkLoaded(entry.getKey())) {
					break;
				}
				entry.getValue().functionCall.run();
				if (blockEntities.containsKey(entry.getKey())) {
					world.removeBlockEntity(entry.getKey());
					world.addBlockEntity(blockEntities.get(entry.getKey()));
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
				if (!world.isChunkLoaded(entity.getBlockPos())) {
					break;
				}
				world.spawnEntity(entity);
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
	public ServerWorld toServerWorld() {
		return world;
	}

	@Override
	public long getTickOrder() {
		return 0;
	}

	@Override
	public QueryableTickScheduler<Block> getBlockTickScheduler() {
		return blockScheduler;
	}

	@Override
	public QueryableTickScheduler<Fluid> getFluidTickScheduler() {
		return fluidScheduler;
	}

	@Override
	public void updateNeighbors(BlockPos pos, Block block) {
		if (blockCache.containsKey(pos)) {
			blockCache.put(pos, blockCache.get(pos).andThen(() -> {
				world.updateNeighbors(pos, block);
			}));
		}
	}

	@Override
	public WorldProperties getLevelProperties() {
		return world.getLevelProperties();
	}

	@Override
	public LocalDifficulty getLocalDifficulty(BlockPos pos) {
		return world.getLocalDifficulty(pos);
	}

	@Nullable
	@Override
	public MinecraftServer getServer() {
		return world.getServer();
	}

	@Override
	public ChunkManager getChunkManager() {
		return world.getChunkManager();
	}

	@Override
	public Random getRandom() {
		return world.getRandom();
	}

	@Override
	public void playSound(@Nullable Entity source, BlockPos pos, SoundEvent sound, SoundCategory category, float volume, float pitch) {
		world.playSound(source, pos, sound, category, volume, pitch);
	}

	@Override
	public void addParticleClient(ParticleEffect parameters, double x, double y, double z, double velocityX, double velocityY, double velocityZ) {
		world.addParticleClient(parameters, x, y, z, velocityX, velocityY, velocityZ);
	}

	@Override
	public void syncWorldEvent(@Nullable Entity player, int eventId, BlockPos pos, int data) {
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
	public LightingProvider getLightingProvider() {
		return world.getLightingProvider();
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
			if (entity.getCachedState() == blockCache.get(pos).state()) {
				return entity;
			}
		}
		if (blockCache.containsKey(pos)) {
			var state = blockCache.get(pos).state();
			if (state.hasBlockEntity()) {
				var blockEntity = ((BlockEntityProvider) state.getBlock()).createBlockEntity(pos.toImmutable(), state);
				blockEntities.put(pos.toImmutable(), blockEntity);
				return blockEntity;
			}
		}
		if (isChunkLoaded(pos)) {
			return world.getBlockEntity(pos);
		} else {
			return null;
		}
	}

	@Override
	public boolean isChunkLoaded(int chunkX, int chunkZ) {
		return false;
	}

	@Override
	public BlockState getBlockState(BlockPos pos) {
		if (blockCache.containsKey(pos)) {
			return blockCache.get(pos).state();
		}
		if (isChunkLoaded(pos)) {
			return world.getBlockState(pos);
		} else {
			return Blocks.AIR.getDefaultState();
		}
	}

	@Override
	public FluidState getFluidState(BlockPos pos) {
		if (blockCache.containsKey(pos)) {
			return blockCache.get(pos).state().getFluidState();
		}
		if (isChunkLoaded(pos)) {
			return world.getFluidState(pos);
		} else {
			return Fluids.EMPTY.getDefaultState();
		}
	}

	@Override
	public List<Entity> getOtherEntities(@Nullable Entity except, Box box, Predicate<? super Entity> predicate) {
		return world.getOtherEntities(except, box, predicate);
	}

	@Override
	public boolean spawnEntity(Entity entity) {
		return entities.add(entity);
	}

	@Override
	public <T extends Entity> List<T> getEntitiesByType(TypeFilter<Entity, T> filter, Box box, Predicate<? super T> predicate) {
		return world.getEntitiesByType(filter, box, predicate);
	}

	@Override
	public List<? extends PlayerEntity> getPlayers() {
		return world.getPlayers();
	}

	@Override
	public boolean setBlockState(BlockPos pos, BlockState state, int flags, int maxUpdateDepth) {
		var actualPos = pos.toImmutable();
		var entry = new BlockEntry(state, () -> {
			world.setBlockState(actualPos, state, flags, maxUpdateDepth);
		});
		return !entry.equals(blockCache.put(pos.toImmutable(), entry));
	}

	@Override
	public boolean removeBlock(BlockPos pos, boolean move) {
		var actualPos = pos.toImmutable();
		var entry = new BlockEntry(
				getBlockState(pos).getFluidState().getBlockState(),
				() -> world.removeBlock(actualPos, move)
		);
		return !entry.equals(blockCache.put(pos.toImmutable(), entry));
	}

	@Override
	public boolean breakBlock(BlockPos pos, boolean drop, @Nullable Entity breakingEntity, int maxUpdateDepth) {
		var actualPos = pos.toImmutable();
		var entry = new BlockEntry(
				getBlockState(pos).getFluidState().getBlockState(),
				() -> world.breakBlock(actualPos, drop, breakingEntity, maxUpdateDepth)
		);
		return !entry.equals(blockCache.put(actualPos, entry));
	}

	@Override
	public boolean testBlockState(BlockPos pos, Predicate<BlockState> state) {
		if (blockCache.containsKey(pos)) {
			return state.test(blockCache.get(pos).state());
		}
		if (isChunkLoaded(pos)) {
			return world.testBlockState(pos, state);
		} else {
			return state.test(Blocks.AIR.getDefaultState());
		}
	}

	@Override
	public boolean testFluidState(BlockPos pos, Predicate<FluidState> state) {
		if (blockCache.containsKey(pos)) {
			return state.test(blockCache.get(pos).state().getFluidState());
		}
		if (isChunkLoaded(pos)) {
			return world.testFluidState(pos, state);
		} else {
			return state.test(Fluids.EMPTY.getDefaultState());
		}
	}

	@Nullable
	@Override
	public Chunk getChunk(int chunkX, int chunkZ, ChunkStatus leastStatus, boolean create) {
		return world.getChunk(chunkX, chunkZ, leastStatus, create);
	}

	@Override
	public int getTopY(Heightmap.Type heightmap, int x, int z) {
		return world.getTopY(heightmap, x, z);
	}

	@Override
	public int getAmbientDarkness() {
		return world.getAmbientDarkness();
	}

	@Override
	public BiomeAccess getBiomeAccess() {
		return world.getBiomeAccess();
	}

	@Override
	public RegistryEntry<Biome> getGeneratorStoredBiome(int biomeX, int biomeY, int biomeZ) {
		return world.getGeneratorStoredBiome(biomeX, biomeY, biomeZ);
	}

	@Override
	public boolean isClient() {
		return world.isClient();
	}

	@Override
	public int getSeaLevel() {
		return world.getSeaLevel();
	}

	@Override
	public DimensionType getDimension() {
		return world.getDimension();
	}

	@Override
	public DynamicRegistryManager getRegistryManager() {
		return world.getRegistryManager();
	}

	@Override
	public FeatureSet getEnabledFeatures() {
		return world.getEnabledFeatures();
	}

	public record BlockEntry(BlockState state, Runnable functionCall) {

		public BlockEntry andThen(Runnable runnable) {
			return new BlockEntry(state, () -> {
				functionCall.run();
				runnable.run();
			});
		}

	}

	public class DeferredTickScheduler<T> implements QueryableTickScheduler<T> {

		private final Supplier<QueryableTickScheduler<T>> scheduler;

		public DeferredTickScheduler(Supplier<QueryableTickScheduler<T>> scheduler) {
			this.scheduler = scheduler;
		}

		@Override
		public boolean isTicking(BlockPos pos, T type) {
			return false;
		}

		@Override
		public void scheduleTick(OrderedTick<T> orderedTick) {
			if (blockCache.containsKey(orderedTick.pos())) {
				blockCache.put(orderedTick.pos(), blockCache.get(orderedTick.pos()).andThen(() -> {
					scheduler.get().scheduleTick(
						new OrderedTick<>(orderedTick.type(), orderedTick.pos(), orderedTick.triggerTick(), world.getTickOrder())
					);
				}));
			}
		}

		@Override
		public boolean isQueued(BlockPos pos, T type) {
			return false;
		}

		@Override
		public int getTickCount() {
			return 0;
		}
	}
}
