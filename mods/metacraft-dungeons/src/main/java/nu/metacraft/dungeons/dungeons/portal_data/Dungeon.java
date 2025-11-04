package nu.metacraft.dungeons.dungeons.portal_data;

import com.google.common.collect.ImmutableList;
import com.mojang.jtracy.TracyClient;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JavaOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.TracingExecutor;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.pools.DimensionPadding;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasBinding;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.jetbrains.annotations.Nullable;
import org.pcollections.HashTreePSet;
import org.pcollections.PSet;
import nu.metacraft.core.block.entities.PortalEntity;
import nu.metacraft.core.portal.PortalTarget;
import nu.metacraft.core.portal.PortalTargetRegistry;
import nu.metacraft.dungeons.DungeonTickets;
import nu.metacraft.dungeons.METAcraftDungeons;
import nu.metacraft.dungeons.dungeons.DungeonData;
import nu.metacraft.dungeons.dungeons.WorldCache;
import nu.metacraft.dungeons.dungeons.datablocks.DataBlock;
import nu.metacraft.dungeons.dungeons.datablocks.DataBlockRegistry;
import nu.metacraft.dungeons.dungeons.datablocks.MultiDataBlock;
import nu.metacraft.dungeons.dungeons.datablocks.PortalDeeper;
import nu.metacraft.dungeons.extensions.ServerLevelExtension;
import nu.metacraft.dungeons.util.ChunkHelper;
import nu.metacraft.lib.util.TaskScheduler;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

public record Dungeon(
		ResourceKey<Level> dungeonDimension,
		ResourceKey<StructureTemplatePool> jigsawPool,
		int maxSize, Optional<Integer> maxDistanceFromCenter,
		List<PoolAliasBinding> aliases,
		Optional<BlockPos> currentDungeon,
		List<DepthSpecificPoolEntry> pools,
		int dungeonDepth,
		int depthOffset,
		boolean generating,
		PSet<ServerPlayer> playersToNotify,
		List<BlockPos> portalsToInitialize,
		int tries
) implements PortalTarget {

	private static final Codec<List<PoolAliasBinding>> ALIAS_BINDING_LIST_CODEC = PoolAliasBinding.CODEC.listOf();

	private static final PSet<ServerPlayer> EMPTY_PLAYERS = HashTreePSet.empty();

	private static final AtomicInteger THREAD_COUNT = new AtomicInteger(0);

	private static final TracingExecutor DUNGEONS = createWorker("Dungeons", false);

	private static TracingExecutor createWorker(String namePrefix, boolean daemon) {
		AtomicInteger atomicInteger = new AtomicInteger(1);
		return new TracingExecutor(Executors.newCachedThreadPool(runnable -> {
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
					Level.RESOURCE_KEY_CODEC.fieldOf("dungeon_dimension").forGetter(Dungeon::dungeonDimension),
					ResourceKey.codec(Registries.TEMPLATE_POOL).fieldOf("jigsaw_pool").forGetter(Dungeon::jigsawPool),
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
			ResourceKey<Level> dungeonDimension,
			ResourceKey<StructureTemplatePool> jigsawPool,
			int maxSize, Optional<Integer> maxDistanceFromCenter,
			List<PoolAliasBinding> aliases,
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


	public Dungeon withPlayer(ServerPlayer player) {
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
	

	private ServerLevel getDungeonDimension(MinecraftServer server) {
		return server.getLevel(dungeonDimension);
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
	
	public void addPlayerToWaitingSet(PortalEntity portal, ServerPlayer player) {
		setNewState(portal, this.withPlayer(player), false);
	}

	private Optional<PortalTarget> getOverride(PortalEntity portal) {
		PortalTarget chosenEntry = null;
		var choices = pools.stream().filter(entry -> entry.depthRange().matches(dungeonDepth)).toList();
		if (!choices.isEmpty()) {
			chosenEntry = choices.get(portal.getLevel().getRandom().nextInt(choices.size())).portal;
		}
		if (chosenEntry != null && chosenEntry != this) {
			portal.setTarget(chosenEntry, true);
			portal.initializeTarget();
			chosenEntry.getFixedTarget(portal).ifPresent(
					t -> {
						if (t.dimension() != dungeonDimension && portal.getLevel().dimension() == dungeonDimension) {
							var world = portal.getLevel().getServer().getLevel(t.dimension());
							PortalEntity.findPortal(world, t.pos()).ifPresent(
									p -> {
										p.getTarget().getFixedTarget(p).ifPresent(
											backTarget -> {
												if (backTarget.dimension() == dungeonDimension) {
													DungeonData.getInstance((ServerLevel) portal.getLevel()).addExternalEntrance(
															t.dimension(), p.getBlockPos()
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
		if (isDungeonResetting(portal.getLevel().getServer())) {
			return DataResult.error(() -> "Dungeon dimension still resetting, please wait...");
		}

		var override = getOverride(portal);
		if (override.isPresent()) {
			return override.get().getOrInitializeTargetForEntity(portal, entity);
		}

		String msg = "Dungeon still generating, please wait...";
		if (currentDungeon.isEmpty()) {
			List<ServerPlayer> players = new ArrayList<>();
			if (entity instanceof ServerPlayer p) {
				players.add(p);
			} else if (entity.hasExactlyOnePlayerPassenger()) {
				for (var e : entity.getIndirectPassengers()) {
					if (e instanceof ServerPlayer p) {
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
						var world = getDungeonDimension(portal.getLevel().getServer());
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
					return DataResult.success(GlobalPos.of(dungeonDimension, pos));
				}
		).orElse(DataResult.error(() -> msg));
	}

	@Override
	public Optional<GlobalPos> getFixedTarget(PortalEntity portal) {
		return currentDungeon.map(pos -> GlobalPos.of(dungeonDimension, pos));
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
		if (portal.getLevel() instanceof ServerLevelExtension w && w.metacraft$isBeingDeleted()) return true;
		return portal.getLevel() == null || portal.getLevel().getServer() == null || portal.getLevel().getServer().isShutdown();
	}
	
	private static Optional<Dungeon> getCurrent(PortalEntity portal) {
		return Optional.ofNullable(portal.getTarget() instanceof Dungeon d ? d : null);
	}

	private static Stream<BlockPos> streamSides(BoundingBox box) {
		return Stream.concat(
				Stream.concat(
						Stream.concat(
								BlockPos.betweenClosedStream(
										new BlockPos(box.minX(), box.minY(), box.minZ()),
										new BlockPos(box.maxX(), box.maxY(), box.minZ())
								),
								BlockPos.betweenClosedStream(
										new BlockPos(box.minX(), box.minY(), box.maxZ()),
										new BlockPos(box.maxX(), box.maxY(), box.maxZ())
								)
						),
						Stream.concat(
								BlockPos.betweenClosedStream(
										new BlockPos(box.minX(), box.minY(), box.minZ()),
										new BlockPos(box.minX(), box.maxY(), box.maxZ())
								),
								BlockPos.betweenClosedStream(
										new BlockPos(box.maxX(), box.minY(), box.minZ()),
										new BlockPos(box.maxX(), box.maxY(), box.maxZ())
								)
						)
				),
				Stream.concat(
						BlockPos.betweenClosedStream(
								new BlockPos(box.minX(), box.minY(), box.minZ()),
								new BlockPos(box.maxX(), box.minY(), box.maxZ())
						),
						BlockPos.betweenClosedStream(
								new BlockPos(box.minX(), box.maxY(), box.minZ()),
								new BlockPos(box.maxX(), box.maxY(), box.maxZ())
						)
				)
		);
	}
	
	private static CompletableFuture<UnaryOperator<Dungeon>> generateDungeon(
			PortalEntity portal, ServerLevel dungeons, BlockPos pos, PoolEntry poolEntry,
			Optional<Structure.GenerationStub> result,
			Structure.GenerationContext context, ChunkGenerator chunkGenerator,
			StructureTemplateManager structureTemplateManager,
			StructureManager structureAccessor
	) {
		var thisPos = new ChunkPos(portal.getBlockPos());
		((ServerLevel) portal.getLevel()).getChunkSource().addTicketWithRadius(DungeonTickets.DUNGEON_ENTRANCE, thisPos, 0);
		return CompletableFuture.supplyAsync(
				() -> {
					THREAD_COUNT.incrementAndGet();
					List<DataBlock.DataBlockEntry<?>> lonelyDataBlocks = new ArrayList<>();
					List<DataBlock.DataMultiBlockEntry<?>> multiBlockDataBlocks = new ArrayList<>();
					StructurePiecesBuilder structurePiecesCollector = result.get().getPiecesBuilder();

					var box = structurePiecesCollector.getBoundingBox();
					var minPos = new ChunkPos(SectionPos.blockToSectionCoord(box.minX()), SectionPos.blockToSectionCoord(box.minZ()));
					var maxPos = new ChunkPos(SectionPos.blockToSectionCoord(box.maxX()), SectionPos.blockToSectionCoord(box.maxZ()));
					var averagePos = new ChunkPos((minPos.x + maxPos.x) / 2, (minPos.z + maxPos.z) / 2);
					int radius = Mth.ceil(Math.max(maxPos.x - minPos.x, maxPos.z - minPos.z)/2.0)+1;

					var randomSeed = context.random().nextLong();

					WorldCache cache = new WorldCache(dungeons);


					streamSides(box.inflatedBy(1, 1, 1)).forEach(bedrockPos -> {
						cache.setBlock(bedrockPos, Blocks.BEDROCK.defaultBlockState(), Block.UPDATE_CLIENTS);
					});


					portal.getLevel().getServer().execute(() -> {
						dungeons.getChunkSource().addTicketWithRadius(DungeonTickets.DUNGEON_ENTRANCE, averagePos, radius);
					});
					
					Runnable onExit = () -> {
						portal.getLevel().getServer().execute(() -> {
							dungeons.getChunkSource().removeTicketWithRadius(DungeonTickets.DUNGEON_ENTRANCE, averagePos, radius);
							((ServerLevel) portal.getLevel()).getChunkSource().removeTicketWithRadius(DungeonTickets.DUNGEON_ENTRANCE, thisPos, 0);
						});
					};

					var parameters = new Parameters(
							dungeons, pos, poolEntry, box
					);

					var random = RandomSource.create(randomSeed);
					for (StructurePiece structurePiece : structurePiecesCollector.build().pieces()) {
						if (!(structurePiece instanceof PoolElementStructurePiece poolStructurePiece)) continue;
						poolStructurePiece.place(cache, structureAccessor, chunkGenerator, random, BoundingBox.infinite(), pos, false);
						if (poolStructurePiece.getElement() instanceof SinglePoolElement simplePool) {
							for (var data : simplePool.getDataMarkers(structureTemplateManager, poolStructurePiece.getPosition(), poolStructurePiece.getRotation(), true)) {
								if (data.nbt() != null) {
									var value = data.nbt().getString("metadata");
									if (value.isEmpty()) continue;
									DataBlockRegistry.PARSER_CODEC.parse(portal.getLevel().registryAccess().createSerializationContext(JavaOps.INSTANCE), value.get()).resultOrPartial(
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

					for (var chunkPos : (Iterable<ChunkPos>) ChunkPos.rangeClosed(minPos, maxPos)::iterator) {
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
						cache.flush(Math.max(Mth.floor(25.0 / Math.max(THREAD_COUNT.get(), 1)), 1));
						try {
							Thread.sleep(50);
						} catch (InterruptedException ignored) {}
					}

					if (shouldThreadStop(portal)) {
						return d -> d.onThreadStop(onExit);
					}

					return portal.getLevel().getServer().submit(() -> {
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
											p -> portalsToInitialize.add(p.getBlockPos())
									);
								}
							});
						}

						if (dungeons.dimension() != portal.getLevel().dimension()) {
							data.addExternalEntrance(portal.getLevel().dimension(), portal.getBlockPos());
						}

						return (UnaryOperator<Dungeon>) d -> {
							if (d.currentDungeon.isPresent()) {
								var playersToNotify = d.playersToNotify;
								for (var player : playersToNotify) {
									player.displayClientMessage(Component.literal("The room you wanted to enter is now ready!"), true);
									player.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 10, 0.5f);
									TaskScheduler.scheduleThrowaway(portal.getLevel().getServer(), () -> {
										player.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 10, 0.75f);
									}, 10);
									TaskScheduler.scheduleThrowaway(portal.getLevel().getServer(), () -> {
										player.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 10, 1);
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
		if (!isDungeonResetting(portal.getLevel().getServer()) && currentDungeon.isEmpty() && !generating) {
			generate(portal);
		}
	}
	private void generate(PortalEntity portal) {
		generate(portal, null);
	}

	private void generate(
			PortalEntity portal, @Nullable BlockPos predefinedPos
	) {
		ServerLevel dungeons = getDungeonDimension(portal.getLevel().getServer());
		if (dungeons != null) {
			var dungeonData = DungeonData.getInstance(dungeons);
			if (dungeonData.isResetting()) {
				METAcraftDungeons.LOGGER.warn("Entrance at " + portal.getBlockPos() + " tried to generate dungeon while resetting.");
				return;
			}
			BlockPos pos = predefinedPos != null ? predefinedPos : dungeonData.getNextSpawnPos();

			var poolRegistry = dungeons.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL);
			if (!poolRegistry.containsKey(jigsawPool)) {
				METAcraftDungeons.LOGGER.warn("Entrance at " + portal.getBlockPos() + " tried to use an unregistered jigsaw pool.");
				return;
			}
			Holder.Reference<StructureTemplatePool> structurePool = poolRegistry.getOrThrow(jigsawPool);
			ChunkGenerator chunkGenerator = dungeons.getChunkSource().getGenerator();
			StructureTemplateManager structureTemplateManager = dungeons.getStructureManager();
			StructureManager structureAccessor = dungeons.structureManager();
			Structure.GenerationContext context = new Structure.GenerationContext(
					dungeons.registryAccess(), chunkGenerator, chunkGenerator.getBiomeSource(), dungeons.getChunkSource().randomState(), structureTemplateManager, dungeons.getRandom().nextLong(),
					new ChunkPos(pos), dungeons, biome -> true
			);
			var result = JigsawPlacement.addPieces(
					context, structurePool, Optional.empty(), maxSize, pos, false,
					Optional.empty(), new JigsawStructure.MaxDistance(
							maxDistanceFromCenter.orElse(dungeonData.getDungeonWidth()/2),
							maxDistanceFromCenter.orElse(dungeonData.getDungeonWidth()/2)
					),
					PoolAliasLookup.create(aliases, pos, dungeons.getRandom().nextLong()), new DimensionPadding(0),
					LiquidSettings.IGNORE_WATERLOGGING
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
							portal.getLevel().getServer().execute(() -> {
								var target = portal.getTarget();
								if (target instanceof Dungeon d) {
									var resultDungeon = dungeon.apply(d);
									if (resultDungeon.currentDungeon.isPresent()) {
										resultDungeon = resultDungeon.withTries(0);
									} else {
										resultDungeon = resultDungeon.withTries(resultDungeon.tries+1);
										if (resultDungeon.tries < 3) {
											METAcraftDungeons.LOGGER.warn("Failed to find entrance for dungeon with entrance at {}, trying again", portal.getBlockPos().toShortString());
											resultDungeon.generate(portal, pos);
											return;
										} else {
											METAcraftDungeons.LOGGER.error("Gave up trying to find entrance for dungeon with entrance at {}", portal.getBlockPos().toShortString());
										}
									}
									setNewState(portal, resultDungeon, true);
								}
							});
						}
				);


			} else {
				METAcraftDungeons.LOGGER.error("Entrance at " + portal.getBlockPos() + " could not generate dungeon.");
			}

		} else {
			METAcraftDungeons.LOGGER.error("Entrance at " + portal.getBlockPos() + " had no valid dungeon dimension.");
		}
	}

	@Override
	public PortalTargetRegistry.PortalTargetType<?> getType() {
		return DungeonPortalTargets.DUNGEON;
	}

	public record DepthSpecificPoolEntry(MinMaxBounds.Ints depthRange, PortalTarget portal) {
		public static final Codec<DepthSpecificPoolEntry> CODEC = RecordCodecBuilder.create(
				instance -> instance.group(
						MinMaxBounds.Ints.CODEC.fieldOf("depth_range").forGetter(DepthSpecificPoolEntry::depthRange),
						Codec.lazyInitialized(() -> PortalTargetRegistry.CODEC).fieldOf("portal").forGetter(DepthSpecificPoolEntry::portal)
				).apply(instance, DepthSpecificPoolEntry::new)
		);
	}

	public record PoolEntry(
			ResourceKey<StructureTemplatePool> jigsawPool,
			List<PoolAliasBinding> aliases,
			int depthOffset, int maxSize, Optional<Integer> maxDistanceFromCenter
	) {

	}

	public static class Parameters {

		public final ServerLevel dungeons;
		public final BlockPos spawnPos;
		public final PoolEntry entry;
		public final BoundingBox structureBounds;
		public boolean foundEntrance = false;

		public Parameters(ServerLevel dungeons, BlockPos spawnPos, PoolEntry entry, BoundingBox structureBounds) {
			this.dungeons = dungeons;
			this.spawnPos = spawnPos;
			this.entry = entry;
			this.structureBounds = structureBounds;
		}
	}
}
