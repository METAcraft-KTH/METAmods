package se.datasektionen.mc.metacraft_dungeons.dungeons.portal_data;

import com.google.common.collect.ImmutableList;
import com.mojang.jtracy.TracyClient;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JavaOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.predicate.NumberRange;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.structure.*;
import net.minecraft.structure.pool.SinglePoolElement;
import net.minecraft.structure.pool.StructurePool;
import net.minecraft.structure.pool.StructurePoolBasedGenerator;
import net.minecraft.structure.pool.alias.StructurePoolAliasBinding;
import net.minecraft.structure.pool.alias.StructurePoolAliasLookup;
import net.minecraft.text.Text;
import net.minecraft.util.math.*;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.thread.NameableExecutor;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.structure.DimensionPadding;
import net.minecraft.world.gen.structure.Structure;
import org.jetbrains.annotations.Nullable;
import org.pcollections.HashTreePSet;
import org.pcollections.PSet;
import se.datasektionen.mc.metacraft_core.block.entities.PortalEntity;
import se.datasektionen.mc.metacraft_core.portal.PortalTarget;
import se.datasektionen.mc.metacraft_core.portal.PortalTargetRegistry;
import se.datasektionen.mc.metacraft_dungeons.DungeonTickets;
import se.datasektionen.mc.metacraft_dungeons.METAcraftDungeons;
import se.datasektionen.mc.metacraft_dungeons.dungeons.DungeonData;
import se.datasektionen.mc.metacraft_dungeons.dungeons.WorldCache;
import se.datasektionen.mc.metacraft_dungeons.dungeons.datablocks.DataBlock;
import se.datasektionen.mc.metacraft_dungeons.dungeons.datablocks.DataBlockRegistry;
import se.datasektionen.mc.metacraft_dungeons.dungeons.datablocks.MultiDataBlock;
import se.datasektionen.mc.metacraft_dungeons.dungeons.datablocks.PortalDeeper;
import se.datasektionen.mc.metacraft_dungeons.extensions.ServerWorldExtension;
import se.datasektionen.mc.metacraft_dungeons.util.ChunkHelper;
import se.datasektionen.mc.metacraft_lib.util.TaskScheduler;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

public record Dungeon(
		RegistryKey<World> dungeonDimension,
		RegistryKey<StructurePool> jigsawPool,
		int maxSize, Optional<Integer> maxDistanceFromCenter,
		List<StructurePoolAliasBinding> aliases,
		Optional<BlockPos> currentDungeon,
		List<DepthSpecificPoolEntry> pools,
		int dungeonDepth,
		int depthOffset,
		boolean generating,
		PSet<ServerPlayerEntity> playersToNotify,
		List<BlockPos> portalsToInitialize,
		int tries
) implements PortalTarget {

	private static final Codec<List<StructurePoolAliasBinding>> ALIAS_BINDING_LIST_CODEC = StructurePoolAliasBinding.CODEC.listOf();

	private static final PSet<ServerPlayerEntity> EMPTY_PLAYERS = HashTreePSet.empty();

	private static final AtomicInteger THREAD_COUNT = new AtomicInteger(0);

	private static final NameableExecutor DUNGEONS = createWorker("Dungeons", false);

	private static NameableExecutor createWorker(String namePrefix, boolean daemon) {
		AtomicInteger atomicInteger = new AtomicInteger(1);
		return new NameableExecutor(Executors.newCachedThreadPool(runnable -> {
			Thread thread = new Thread(runnable);
			String string2 = namePrefix + atomicInteger.getAndIncrement();
			TracyClient.setThreadName(string2, namePrefix.hashCode());
			thread.setName(string2);
			thread.setDaemon(daemon);
			thread.setUncaughtExceptionHandler(Dungeon::uncaughtExceptionHandler);
			return thread;
		}));
	}

	private static void uncaughtExceptionHandler(Thread thread, Throwable t) {
		METAcraftDungeons.LOGGER.error(String.format(Locale.ROOT, "Caught exception in thread %s", thread), t);
	}

	public static final MapCodec<Dungeon> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					World.CODEC.fieldOf("dungeon_dimension").forGetter(Dungeon::dungeonDimension),
					RegistryKey.createCodec(RegistryKeys.TEMPLATE_POOL).fieldOf("jigsaw_pool").forGetter(Dungeon::jigsawPool),
					Codec.INT.fieldOf("max_size").forGetter(Dungeon::maxSize),
					Codec.INT.optionalFieldOf("max_distance_from_center").forGetter(Dungeon::maxDistanceFromCenter),
					ALIAS_BINDING_LIST_CODEC.optionalFieldOf("pool_aliases", List.of()).forGetter(Dungeon::aliases),
					BlockPos.CODEC.optionalFieldOf("current_dungeon").forGetter(Dungeon::currentDungeon),
					DepthSpecificPoolEntry.CODEC.listOf().optionalFieldOf("pools", List.of()).forGetter(Dungeon::pools),
					Codec.INT.optionalFieldOf("dungeon_depth", 0).forGetter(Dungeon::dungeonDepth),
					Codec.INT.optionalFieldOf("depth_offset", 1).forGetter(Dungeon::depthOffset)
			).apply(instance, Dungeon::new)
	);
	
	public Dungeon(
			RegistryKey<World> dungeonDimension,
			RegistryKey<StructurePool> jigsawPool,
			int maxSize, Optional<Integer> maxDistanceFromCenter,
			List<StructurePoolAliasBinding> aliases,
			Optional<BlockPos> currentDungeon,
			List<DepthSpecificPoolEntry> pools,
			int dungeonDepth,
			int depthOffset
	) {
		this(
				dungeonDimension, jigsawPool, maxSize, maxDistanceFromCenter, aliases, currentDungeon,
				pools, dungeonDepth, depthOffset, false, EMPTY_PLAYERS, List.of(), 0
		);
	}


	public Dungeon withPlayer(ServerPlayerEntity player) {
		if (playersToNotify.contains(player)) return this;
		return new Dungeon(
				dungeonDimension, jigsawPool, maxSize, maxDistanceFromCenter, aliases,
				currentDungeon, pools, dungeonDepth, depthOffset,
				generating, playersToNotify.plus(player),
				portalsToInitialize, tries
		);
	}
	
	public Dungeon clearPlayers() {
		if (playersToNotify.isEmpty()) return this;
		return new Dungeon(
				dungeonDimension, jigsawPool, maxSize, maxDistanceFromCenter, aliases,
				currentDungeon, pools, dungeonDepth, depthOffset,
				generating, EMPTY_PLAYERS, portalsToInitialize, tries
		);
	}
	
	public Dungeon withDungeon(BlockPos dungeon) {
		if (Objects.equals(currentDungeon.orElse(null), dungeon)) return this;
		return new Dungeon(
				dungeonDimension, jigsawPool, maxSize, maxDistanceFromCenter, aliases,
				Optional.ofNullable(dungeon), pools, dungeonDepth, depthOffset,
				generating, playersToNotify, portalsToInitialize, tries
		);
	}
	
	public Dungeon asGenerating(boolean generating) {
		if (generating == this.generating) return this;
		return new Dungeon(
				dungeonDimension, jigsawPool, maxSize, maxDistanceFromCenter, aliases,
				currentDungeon, pools, dungeonDepth, depthOffset,
				generating, playersToNotify, portalsToInitialize, tries
		);
	}

	public Dungeon withPortalsToInitialize(List<BlockPos> portalsToInitialize) {
		if (this.portalsToInitialize == portalsToInitialize || (this.portalsToInitialize.isEmpty() && portalsToInitialize.isEmpty())) return this;
		return new Dungeon(
				dungeonDimension, jigsawPool, maxSize, maxDistanceFromCenter, aliases,
				currentDungeon, pools, dungeonDepth, depthOffset,
				generating, playersToNotify, portalsToInitialize, tries
		);
	}

	public Dungeon withTries(int tries) {
		return new Dungeon(
				dungeonDimension, jigsawPool, maxSize, maxDistanceFromCenter, aliases,
				currentDungeon, pools, dungeonDepth, depthOffset,
				generating, playersToNotify, portalsToInitialize, tries
		);
	}
	

	private ServerWorld getDungeonDimension(MinecraftServer server) {
		return server.getWorld(dungeonDimension);
	}

	private boolean isDungeonResetting(MinecraftServer server) {
		var dim = getDungeonDimension(server);
		if (dim == null) return true;
		return DungeonData.getIfPresent(dim).map(DungeonData::isResetting).orElse(true);
	}

	@Override
	public PortalTarget getWithPos(BlockPos pos) {
		return withDungeon(pos);
	}

	@Override
	public PortalTarget getAsEmpty() {
		return withDungeon(null);
	}
	
	private static void setNewState(PortalEntity portal, Dungeon data, boolean needsSaving) {
		portal.setTarget(data, needsSaving);
	}
	
	public void addPlayerToWaitingSet(PortalEntity portal, ServerPlayerEntity player) {
		setNewState(portal, this.withPlayer(player), false);
	}

	private Optional<PortalTarget> getOverride(PortalEntity portal) {
		PortalTarget chosenEntry = null;
		var choices = pools.stream().filter(entry -> entry.depthRange().test(dungeonDepth)).toList();
		if (!choices.isEmpty()) {
			chosenEntry = choices.get(portal.getWorld().getRandom().nextInt(choices.size())).portal;
		}
		if (chosenEntry != null && chosenEntry != this) {
			portal.setTarget(chosenEntry, true);
			portal.initializeTarget();
			chosenEntry.getFixedTarget(portal).ifPresent(
					t -> {
						if (t.dimension() != dungeonDimension && portal.getWorld().getRegistryKey() == dungeonDimension) {
							var world = portal.getWorld().getServer().getWorld(t.dimension());
							PortalEntity.findPortal(world, t.pos()).ifPresent(
									p -> {
										p.getTarget().getFixedTarget(p).ifPresent(
											backTarget -> {
												if (backTarget.dimension() == dungeonDimension) {
													DungeonData.getInstance((ServerWorld) portal.getWorld()).addExternalEntrance(
															t.dimension(), p.getPos()
													);
												}
											}
										);
									}
							);
						}
					}
			);
			return Optional.of(chosenEntry);
		}
		return Optional.empty();
	}

	@Override
	public DataResult<GlobalPos> getOrInitializeTargetForEntity(PortalEntity portal, Entity entity) {
		if (isDungeonResetting(portal.getWorld().getServer())) {
			return DataResult.error(() -> "Dungeon dimension still resetting, please wait...");
		}

		var override = getOverride(portal);
		if (override.isPresent()) {
			return override.get().getOrInitializeTargetForEntity(portal, entity);
		}

		String msg = "Dungeon still generating, please wait...";
		if (currentDungeon.isEmpty()) {
			List<ServerPlayerEntity> players = new ArrayList<>();
			if (entity instanceof ServerPlayerEntity p) {
				players.add(p);
			} else if (entity.hasPlayerRider()) {
				for (var e : entity.getPassengersDeep()) {
					if (e instanceof ServerPlayerEntity p) {
						players.add(p);
					}
				}
			}
			for (var player : players) {
				addPlayerToWaitingSet(portal, player);
			}

			if (generating) {
				return DataResult.error(() -> msg);
			}
			getCurrent(portal).ifPresent(d -> d.generate(portal));
		}
		return currentDungeon.map(
				pos -> {
					var current = getCurrent(portal);
					if (current.isPresent()) {
						var world = getDungeonDimension(portal.getWorld().getServer());
						for (var p : current.get().portalsToInitialize) {
							var be = world.getBlockEntity(p);
							if (be instanceof PortalEntity pe) {
								pe.initializeTarget();
							}
						}
						if (!current.get().portalsToInitialize.isEmpty()) {
							setNewState(portal, current.get().withPortalsToInitialize(List.of()), false);
						}
					}
					return DataResult.success(GlobalPos.create(dungeonDimension, pos));
				}
		).orElse(DataResult.error(() -> msg));
	}

	@Override
	public Optional<GlobalPos> getFixedTarget(PortalEntity portal) {
		return currentDungeon.map(pos -> GlobalPos.create(dungeonDimension, pos));
	}

	private Dungeon onThreadStop(Runnable extra) {
		extra.run();
		THREAD_COUNT.decrementAndGet();
		return asGenerating(false);
	}

	private static boolean shouldThreadStop(PortalEntity portal) {
		var current = getCurrent(portal).orElse(null);
		if (portal.getTarget() != current) return true;
		if (portal.isRemoved()) return true;
		if (portal.getWorld() instanceof ServerWorldExtension w && w.metacraft$isBeingDeleted()) return true;
		return portal.getWorld() == null || portal.getWorld().getServer() == null || portal.getWorld().getServer().isStopping();
	}
	
	private static Optional<Dungeon> getCurrent(PortalEntity portal) {
		return Optional.ofNullable(portal.getTarget() instanceof Dungeon d ? d : null);
	}

	private static Stream<BlockPos> streamSides(BlockBox box) {
		return Stream.concat(
				Stream.concat(
						Stream.concat(
								BlockPos.stream(
										new BlockPos(box.getMinX(), box.getMinY(), box.getMinZ()),
										new BlockPos(box.getMaxX(), box.getMaxY(), box.getMinZ())
								),
								BlockPos.stream(
										new BlockPos(box.getMinX(), box.getMinY(), box.getMaxZ()),
										new BlockPos(box.getMaxX(), box.getMaxY(), box.getMaxZ())
								)
						),
						Stream.concat(
								BlockPos.stream(
										new BlockPos(box.getMinX(), box.getMinY(), box.getMinZ()),
										new BlockPos(box.getMinX(), box.getMaxY(), box.getMaxZ())
								),
								BlockPos.stream(
										new BlockPos(box.getMaxX(), box.getMinY(), box.getMinZ()),
										new BlockPos(box.getMaxX(), box.getMaxY(), box.getMaxZ())
								)
						)
				),
				Stream.concat(
						BlockPos.stream(
								new BlockPos(box.getMinX(), box.getMinY(), box.getMinZ()),
								new BlockPos(box.getMaxX(), box.getMinY(), box.getMaxZ())
						),
						BlockPos.stream(
								new BlockPos(box.getMinX(), box.getMaxY(), box.getMinZ()),
								new BlockPos(box.getMaxX(), box.getMaxY(), box.getMaxZ())
						)
				)
		);
	}
	
	private static CompletableFuture<UnaryOperator<Dungeon>> generateDungeon(
			PortalEntity portal, ServerWorld dungeons, BlockPos pos, PoolEntry poolEntry,
			Optional<Structure.StructurePosition> result,
			Structure.Context context, ChunkGenerator chunkGenerator,
			StructureTemplateManager structureTemplateManager,
			StructureAccessor structureAccessor
	) {
		var thisPos = new ChunkPos(portal.getPos());
		((ServerWorld) portal.getWorld()).getChunkManager().addTicket(DungeonTickets.DUNGEON_ENTRANCE, thisPos, 0);
		return CompletableFuture.supplyAsync(
				() -> {
					THREAD_COUNT.incrementAndGet();
					List<DataBlock.DataBlockEntry<?>> lonelyDataBlocks = new ArrayList<>();
					List<DataBlock.DataMultiBlockEntry<?>> multiBlockDataBlocks = new ArrayList<>();
					StructurePiecesCollector structurePiecesCollector = result.get().generate();

					var box = structurePiecesCollector.getBoundingBox();
					var minPos = new ChunkPos(ChunkSectionPos.getSectionCoord(box.getMinX()), ChunkSectionPos.getSectionCoord(box.getMinZ()));
					var maxPos = new ChunkPos(ChunkSectionPos.getSectionCoord(box.getMaxX()), ChunkSectionPos.getSectionCoord(box.getMaxZ()));
					var averagePos = new ChunkPos((minPos.x + maxPos.x) / 2, (minPos.z + maxPos.z) / 2);
					int radius = MathHelper.ceil(Math.max(maxPos.x - minPos.x, maxPos.z - minPos.z)/2.0)+1;

					var randomSeed = context.random().nextLong();

					WorldCache cache = new WorldCache(dungeons);


					streamSides(box.expand(1, 1, 1)).forEach(bedrockPos -> {
						cache.setBlockState(bedrockPos, Blocks.BEDROCK.getDefaultState(), Block.NOTIFY_LISTENERS);
					});


					portal.getWorld().getServer().execute(() -> {
						dungeons.getChunkManager().addTicket(DungeonTickets.DUNGEON_ENTRANCE, averagePos, radius);
					});
					
					Runnable onExit = () -> {
						portal.getWorld().getServer().execute(() -> {
							dungeons.getChunkManager().removeTicket(DungeonTickets.DUNGEON_ENTRANCE, averagePos, radius);
							((ServerWorld) portal.getWorld()).getChunkManager().removeTicket(DungeonTickets.DUNGEON_ENTRANCE, thisPos, 0);
						});
					};

					var parameters = new Parameters(
							dungeons, pos, poolEntry, box
					);

					var random = Random.create(randomSeed);
					for (StructurePiece structurePiece : structurePiecesCollector.toList().pieces()) {
						if (!(structurePiece instanceof PoolStructurePiece poolStructurePiece)) continue;
						poolStructurePiece.generate(cache, structureAccessor, chunkGenerator, random, BlockBox.infinite(), pos, false);
						if (poolStructurePiece.getPoolElement() instanceof SinglePoolElement simplePool) {
							for (var data : simplePool.getDataStructureBlocks(structureTemplateManager, poolStructurePiece.getPos(), poolStructurePiece.getRotation(), true)) {
								if (data.nbt() != null) {
									var value = data.nbt().getString("metadata");
									if (value.isEmpty()) continue;
									DataBlockRegistry.PARSER_CODEC.parse(portal.getWorld().getRegistryManager().getOps(JavaOps.INSTANCE), value.get()).resultOrPartial(
											METAcraftDungeons.LOGGER::error
									).ifPresent(dataBlock -> {
										dataBlock.initialise(portal, parameters);
										if (dataBlock instanceof MultiDataBlock multi) {
											multiBlockDataBlocks.add(new DataBlock.DataMultiBlockEntry<>(data.pos(), poolStructurePiece, (DataBlock & MultiDataBlock) multi));
										} else {
											lonelyDataBlocks.add(new DataBlock.DataBlockEntry<>(data.pos(), poolStructurePiece, dataBlock));
										}
									});
								}
							}
						}
					}
					
					if (shouldThreadStop(portal)) {
						return d -> d.onThreadStop(onExit);
					}

					for (var chunkPos : (Iterable<ChunkPos>) ChunkPos.stream(minPos, maxPos)::iterator) {
						CountDownLatch done = new CountDownLatch(1);

						AtomicBoolean shouldContinue = new AtomicBoolean(true);
						ChunkHelper.whenChunkReady(dungeons, chunkPos, ChunkStatus.FULL, chunk -> {
							if (chunk.isEmpty()) {
								shouldContinue.set(false);
							}
							done.countDown();
						});
						try {
							done.await();
							if (!shouldContinue.get()) {
								return d -> d.onThreadStop(onExit);
							}
						} catch (InterruptedException ignored) {}
					}

					while (!cache.isEmpty()) {
						if (shouldThreadStop(portal)) {
							return d -> d.onThreadStop(onExit);
						}
						cache.flush(Math.max(MathHelper.floor(25.0 / Math.max(THREAD_COUNT.get(), 1)), 1));
						try {
							Thread.sleep(50);
						} catch (InterruptedException ignored) {}
					}

					if (shouldThreadStop(portal)) {
						return d -> d.onThreadStop(onExit);
					}

					return portal.getWorld().getServer().submit(() -> {
						ImmutableList.Builder<BlockPos> portalsToInitialize = new ImmutableList.Builder<>();
						var data = DungeonData.getInstance(dungeons);
						var dataBlockSets = MultiDataBlock.merge(multiBlockDataBlocks);

						for (var dataBlock : lonelyDataBlocks) {
							dataBlock.datablock().processDataBlock(dataBlock.pos(), dataBlock.piece());
						}
						for (var dataBlock : multiBlockDataBlocks) {
							dataBlock.datablock().processDataBlock(dataBlock.pos(), dataBlock.piece());
						}
						for (var key : dataBlockSets.keySet()) {
							dataBlockSets.get(key).stream().max(
									Comparator.comparing(entry -> entry.datablock().getPriority())
							).ifPresent(best -> {
								best.datablock().processDataBlocks(dataBlockSets.get(key));
								if (best.datablock() instanceof PortalDeeper) {
									PortalEntity.findPortal(dungeons, best.pos()).ifPresent(
											p -> portalsToInitialize.add(p.getPos())
									);
								}
							});
						}

						if (dungeons.getRegistryKey() != portal.getWorld().getRegistryKey()) {
							data.addExternalEntrance(portal.getWorld().getRegistryKey(), portal.getPos());
						}

						return (UnaryOperator<Dungeon>) d -> {
							if (d.currentDungeon.isPresent()) {
								var playersToNotify = d.playersToNotify;
								for (var player : playersToNotify) {
									player.sendMessage(Text.literal("The room you wanted to enter is now ready!"), true);
									player.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.BLOCKS, 10, 0.5f);
									TaskScheduler.schedule(portal.getWorld().getServer(), () -> {
										player.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.BLOCKS, 10, 0.75f);
									}, 10);
									TaskScheduler.schedule(portal.getWorld().getServer(), () -> {
										player.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.BLOCKS, 10, 1);
									}, 20);
								}
								d = d.clearPlayers().withPortalsToInitialize(portalsToInitialize.build());
							}
							return d.onThreadStop(onExit);
						};
					}).join();
				},
				DUNGEONS
		);
	}

	@Override
	public void initialize(PortalEntity portal) {
		var override = getOverride(portal);
		if (override.isPresent()) {
			override.get().initialize(portal);
			return;
		}
		if (!isDungeonResetting(portal.getWorld().getServer()) && currentDungeon.isEmpty() && !generating) {
			generate(portal);
		}
	}
	private void generate(PortalEntity portal) {
		generate(portal, null);
	}

	private void generate(
			PortalEntity portal, @Nullable BlockPos predefinedPos
	) {
		ServerWorld dungeons = getDungeonDimension(portal.getWorld().getServer());
		if (dungeons != null) {
			var dungeonData = DungeonData.getInstance(dungeons);
			if (dungeonData.isResetting()) {
				METAcraftDungeons.LOGGER.warn("Entrance at " + portal.getPos() + " tried to generate dungeon while resetting.");
				return;
			}
			BlockPos pos = predefinedPos != null ? predefinedPos : dungeonData.getNextSpawnPos();

			var poolRegistry = dungeons.getRegistryManager().getOrThrow(RegistryKeys.TEMPLATE_POOL);
			if (!poolRegistry.contains(jigsawPool)) {
				METAcraftDungeons.LOGGER.warn("Entrance at " + portal.getPos() + " tried to use an unregistered jigsaw pool.");
				return;
			}
			RegistryEntry.Reference<StructurePool> structurePool = poolRegistry.getOrThrow(jigsawPool);
			ChunkGenerator chunkGenerator = dungeons.getChunkManager().getChunkGenerator();
			StructureTemplateManager structureTemplateManager = dungeons.getStructureTemplateManager();
			StructureAccessor structureAccessor = dungeons.getStructureAccessor();
			Structure.Context context = new Structure.Context(
					dungeons.getRegistryManager(), chunkGenerator, chunkGenerator.getBiomeSource(), dungeons.getChunkManager().getNoiseConfig(), structureTemplateManager, dungeons.getRandom().nextLong(),
					new ChunkPos(pos), dungeons, biome -> true
			);
			var result = StructurePoolBasedGenerator.generate(
					context, structurePool, Optional.empty(), maxSize, pos, false,
					Optional.empty(), maxDistanceFromCenter.orElse(dungeonData.getDungeonWidth()/2),
					StructurePoolAliasLookup.create(aliases, pos, dungeons.getRandom().nextLong()), new DimensionPadding(0),
					StructureLiquidSettings.IGNORE_WATERLOGGING
			);

			if (result.isPresent()) {
				THREAD_COUNT.incrementAndGet();
				setNewState(portal, asGenerating(true), false);
				generateDungeon(
						portal, dungeons, pos,
						new PoolEntry(jigsawPool, aliases, 1, maxSize, maxDistanceFromCenter),
						result, context, chunkGenerator, structureTemplateManager, structureAccessor
				).thenAccept(
						dungeon -> {
							portal.getWorld().getServer().execute(() -> {
								var target = portal.getTarget();
								if (target instanceof Dungeon d) {
									var resultDungeon = dungeon.apply(d);
									if (resultDungeon.currentDungeon.isPresent()) {
										resultDungeon = resultDungeon.withTries(0);
									} else {
										resultDungeon = resultDungeon.withTries(resultDungeon.tries+1);
										if (resultDungeon.tries < 3) {
											METAcraftDungeons.LOGGER.warn("Failed to find entrance for dungeon with entrance at {}, trying again", portal.getPos().toShortString());
											resultDungeon.generate(portal, pos);
											return;
										} else {
											METAcraftDungeons.LOGGER.error("Gave up trying to find entrance for dungeon with entrance at {}", portal.getPos().toShortString());
										}
									}
									setNewState(portal, resultDungeon, true);
								}
							});
						}
				);


			} else {
				METAcraftDungeons.LOGGER.error("Entrance at " + portal.getPos() + " could not generate dungeon.");
			}

		} else {
			METAcraftDungeons.LOGGER.error("Entrance at " + portal.getPos() + " had no valid dungeon dimension.");
		}
	}

	@Override
	public PortalTargetRegistry.PortalTargetType<?> getType() {
		return DungeonPortalTargets.DUNGEON;
	}

	public record DepthSpecificPoolEntry(NumberRange.IntRange depthRange, PortalTarget portal) {
		public static final Codec<DepthSpecificPoolEntry> CODEC = RecordCodecBuilder.create(
				instance -> instance.group(
						NumberRange.IntRange.CODEC.fieldOf("depth_range").forGetter(DepthSpecificPoolEntry::depthRange),
						Codec.lazyInitialized(() -> PortalTargetRegistry.CODEC).fieldOf("portal").forGetter(DepthSpecificPoolEntry::portal)
				).apply(instance, DepthSpecificPoolEntry::new)
		);
	}

	public record PoolEntry(
			RegistryKey<StructurePool> jigsawPool,
			List<StructurePoolAliasBinding> aliases,
			int depthOffset, int maxSize, Optional<Integer> maxDistanceFromCenter
	) {

	}

	public static class Parameters {

		public final ServerWorld dungeons;
		public final BlockPos spawnPos;
		public final PoolEntry entry;
		public final BlockBox structureBounds;
		public boolean foundEntrance = false;

		public Parameters(ServerWorld dungeons, BlockPos spawnPos, PoolEntry entry, BlockBox structureBounds) {
			this.dungeons = dungeons;
			this.spawnPos = spawnPos;
			this.entry = entry;
			this.structureBounds = structureBounds;
		}
	}
}
