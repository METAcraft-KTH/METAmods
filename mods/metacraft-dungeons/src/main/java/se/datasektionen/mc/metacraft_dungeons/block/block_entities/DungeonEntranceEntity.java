package se.datasektionen.mc.metacraft_dungeons.block.block_entities;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JavaOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.predicate.NumberRange;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicketType;
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
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.structure.DimensionPadding;
import net.minecraft.world.gen.structure.Structure;
import se.datasektionen.mc.metacraft_core.block.entities.PortalEntity;
import se.datasektionen.mc.metacraft_dungeons.Dimensions;
import se.datasektionen.mc.metacraft_dungeons.METAcraftDungeons;
import se.datasektionen.mc.metacraft_dungeons.block.DungeonsBlockEntities;
import se.datasektionen.mc.metacraft_dungeons.extensions.ServerWorldExtension;
import se.datasektionen.mc.metacraft_lib.util.TaskScheduler;
import se.datasektionen.mc.metacraft_dungeons.dungeons.DungeonData;
import se.datasektionen.mc.metacraft_dungeons.dungeons.WorldCache;
import se.datasektionen.mc.metacraft_dungeons.dungeons.datablocks.DataBlock;
import se.datasektionen.mc.metacraft_dungeons.dungeons.datablocks.DataBlockRegistry;
import se.datasektionen.mc.metacraft_dungeons.dungeons.datablocks.MultiDataBlock;
import se.datasektionen.mc.metacraft_dungeons.util.ChunkHelper;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class DungeonEntranceEntity extends PortalEntity {

	private static final AtomicInteger THREAD_COUNT = new AtomicInteger(0);

	private static final Codec<List<StructurePoolAliasBinding>> ALIAS_BINDING_LIST_CODEC = Codec.lazyInitialized(
			StructurePoolAliasBinding.CODEC::listOf
	);

	private static final String JIGSAW = "Jigsaw";
	private static final String MAX_SIZE = "MaxSize";
	private static final String POOL = "Pool";
	private static final String ALIASES = "PoolAliases";
	private static final String DEPTH_SPECIFIC_POOLS = "DepthSpecificPools";
	private static final String DEPTH = "DungeonDepth";
	private static final String DEPTH_OFFSET = "DepthOffset";
	protected RegistryKey<StructurePool> jigsawPool;
	protected int maxSize = 7;
	protected int dungeonDepth = 0;
	protected int depthOffset = 1;
	protected List<PoolEntry> depthSpecificPools = new ArrayList<>();

	protected List<StructurePoolAliasBinding> aliases = new ArrayList<>();

	private Thread chunkGeneratorThread = null;
	private static final ChunkTicketType<ChunkPos> TICKET = ChunkTicketType.create(
			METAcraftDungeons.getID("dungeon_entrance").toString(),
			Comparator.comparingLong(ChunkPos::toLong)
	);

	private final Set<ServerPlayerEntity> playersToNotify = new HashSet<>();

	public DungeonEntranceEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	public DungeonEntranceEntity(BlockPos pos, BlockState state) {
		super(DungeonsBlockEntities.DUNGEON_ENTRANCE, pos, state);
	}

	@Override
	public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		super.readNbt(nbt, lookup);
		if (nbt.contains(JIGSAW)) {
			NbtCompound jigsaw = nbt.getCompound(JIGSAW);
			jigsawPool = Optional.ofNullable(Identifier.tryParse(jigsaw.getString(POOL))).map(
					id -> RegistryKey.of(RegistryKeys.TEMPLATE_POOL, id)
			).orElse(null);
			if (jigsaw.contains(MAX_SIZE)) {
				maxSize = jigsaw.getInt(MAX_SIZE);
			}
			if (jigsaw.contains(DEPTH_SPECIFIC_POOLS)) {
				PoolEntry.CODEC.listOf().parse(lookup.getOps(NbtOps.INSTANCE), jigsaw.get(DEPTH_SPECIFIC_POOLS)).resultOrPartial(
						METAcraftDungeons.LOGGER::error
				).ifPresent(entries -> {
					this.depthSpecificPools = entries;
				});
			} else {
				this.depthSpecificPools = new ArrayList<>();
			}
			if (jigsaw.contains(ALIASES)) {
				ALIAS_BINDING_LIST_CODEC.parse(lookup.getOps(NbtOps.INSTANCE), jigsaw.get(ALIASES)).resultOrPartial(
						METAcraftDungeons.LOGGER::error
				).ifPresent(aliases -> {
					this.aliases = aliases;
				});
			} else {
				this.aliases = new ArrayList<>();
			}
		} else {
			jigsawPool = null;
		}
		dungeonDepth = nbt.getInt(DEPTH);
		depthOffset = nbt.getInt(DEPTH_OFFSET);

		startGenerating();
	}

	@Override
	protected void onUnlocked(PlayerEntity player, Hand hand, ItemStack stack) {
		super.onUnlocked(player, hand, stack);
		startGenerating();
	}

	@Override
	public void setWorld(World world) {
		super.setWorld(world);
		startGenerating();
		if (jigsawPool == null) {
			jigsawPool = RegistryKey.of(RegistryKeys.TEMPLATE_POOL, METAcraftDungeons.getID("dungeon_entrance"));
			depthSpecificPools.add(
					new PoolEntry(
							RegistryKey.of(RegistryKeys.TEMPLATE_POOL, METAcraftDungeons.getID("treasure_room")),
							NumberRange.IntRange.atLeast(25),
							aliases, 1, maxSize
					)
			);
			//We intentionally initialize this after adding depthSpecificPools since they won't be used in the treasure room.
			this.aliases = TrialChamberData.ALIAS_BINDINGS;
		}
	}

	@Override
	public void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		super.writeNbt(nbt, lookup);
		if (jigsawPool != null) {
			NbtCompound jigsaw = new NbtCompound();
			jigsaw.putString(POOL, jigsawPool.getValue().toString());
			jigsaw.putInt(MAX_SIZE, maxSize);
			if (!this.depthSpecificPools.isEmpty()) {
				PoolEntry.CODEC.listOf().encodeStart(lookup.getOps(NbtOps.INSTANCE), depthSpecificPools).resultOrPartial(
						METAcraftDungeons.LOGGER::error
				).ifPresent(value -> {
					jigsaw.put(DEPTH_SPECIFIC_POOLS, value);
				});
			}
			if (!aliases.isEmpty()) {
				ALIAS_BINDING_LIST_CODEC.encodeStart(lookup.getOps(NbtOps.INSTANCE), aliases).resultOrPartial(
						METAcraftDungeons.LOGGER::error
				).ifPresent(encodedAliases -> {
					jigsaw.put(ALIASES, encodedAliases);
				});
			}
			nbt.put(JIGSAW, jigsaw);
		}
		nbt.putInt(DEPTH, dungeonDepth);
		nbt.putInt(DEPTH_OFFSET, depthOffset);
	}

	private boolean shouldThreadStop() {
		if (this.removed) return true;
		if (world instanceof ServerWorldExtension w && w.metacraft$isBeingDeleted()) return true;
		return getWorld() == null || getWorld().getServer() == null || getWorld().getServer().isStopping();
	}

	private void onThreadStop() {
		THREAD_COUNT.decrementAndGet();
		chunkGeneratorThread = null;
	}

	private ServerWorld getDungeonDimension() {
		return world.getServer().getWorld(Optional.ofNullable(targetDim).orElse(Dimensions.DUNGEONS));
	}

	public void startGenerating() {
		if (world == null) return;
		if (targetPos != null) return;
		if (isLocked()) return;

		PoolEntry chosenEntry;
		var choices = depthSpecificPools.stream().filter(entry -> entry.depthRange.test(dungeonDepth)).toList();
		if (!choices.isEmpty()) {
			chosenEntry = choices.get(world.getRandom().nextInt(choices.size()));
		} else {
			chosenEntry = new PoolEntry(jigsawPool, NumberRange.IntRange.ANY, aliases, depthOffset, maxSize);
		}
		if (chosenEntry.jigsawPool != null) {
			ServerWorld dungeons = getDungeonDimension();
			if (dungeons != null) {
				var dungeonData = DungeonData.getInstance(dungeons);
				if (dungeonData.isResetting()) {
					METAcraftDungeons.LOGGER.warn("Entrance at " + this.pos + " tried to generate dungeon while resetting.");
					return;
				}
				BlockPos pos = dungeonData.getNextSpawnPos();

				var poolRegistry = dungeons.getRegistryManager().getOrThrow(RegistryKeys.TEMPLATE_POOL);
				if (!poolRegistry.contains(chosenEntry.jigsawPool)) {
					METAcraftDungeons.LOGGER.warn("Entrance at " + this.pos + " tried to use an unregistered jigsaw pool.");
					return;
				}
				RegistryEntry.Reference<StructurePool> structurePool = poolRegistry.getOrThrow(chosenEntry.jigsawPool);
				ChunkGenerator chunkGenerator = dungeons.getChunkManager().getChunkGenerator();
				StructureTemplateManager structureTemplateManager = dungeons.getStructureTemplateManager();
				StructureAccessor structureAccessor = dungeons.getStructureAccessor();
				Structure.Context context = new Structure.Context(
						dungeons.getRegistryManager(), chunkGenerator, chunkGenerator.getBiomeSource(), dungeons.getChunkManager().getNoiseConfig(), structureTemplateManager, dungeons.getRandom().nextLong(),
						new ChunkPos(pos), dungeons, biome -> true
				);
				var result = StructurePoolBasedGenerator.generate(
						context, structurePool, Optional.empty(), chosenEntry.maxSize, pos, false,
						Optional.empty(), dungeonData.getDungeonWidth()/2,
						StructurePoolAliasLookup.create(chosenEntry.aliases, pos, dungeons.getRandom().nextLong()), new DimensionPadding(0),
						StructureLiquidSettings.IGNORE_WATERLOGGING
				);

				if (result.isPresent()) {
					Parameters parameters = new Parameters(dungeons, pos, chosenEntry);
					List<DataBlockEntry<?>> lonelyDataBlocks = new ArrayList<>();
					List<DataMultiBlockEntry<?>> multiBlockDataBlocks = new ArrayList<>();
					StructurePiecesCollector structurePiecesCollector = result.get().generate();

					var box = structurePiecesCollector.getBoundingBox();
					var minPos = new ChunkPos(ChunkSectionPos.getSectionCoord(box.getMinX()), ChunkSectionPos.getSectionCoord(box.getMinZ()));
					var maxPos = new ChunkPos(ChunkSectionPos.getSectionCoord(box.getMaxX()), ChunkSectionPos.getSectionCoord(box.getMaxZ()));
					var averagePos = new ChunkPos((minPos.x + maxPos.x) / 2, (minPos.z + maxPos.z) / 2);
					int radius = MathHelper.ceil(Math.max(maxPos.x - minPos.x, maxPos.z - minPos.z)/2.0)+1;

					var randomSeed = context.random().nextLong();

					var thisPos = new ChunkPos(this.getPos());

					THREAD_COUNT.incrementAndGet();
					chunkGeneratorThread = new Thread(() -> {
						WorldCache cache = new WorldCache(dungeons);

						var random = Random.create(randomSeed);
						for (StructurePiece structurePiece : structurePiecesCollector.toList().pieces()) {
							if (!(structurePiece instanceof PoolStructurePiece poolStructurePiece)) continue;
							poolStructurePiece.generate(cache, structureAccessor, chunkGenerator, random, BlockBox.infinite(), pos, false);
							if (poolStructurePiece.getPoolElement() instanceof SinglePoolElement simplePool) {
								for (var data : simplePool.getDataStructureBlocks(structureTemplateManager, poolStructurePiece.getPos(), poolStructurePiece.getRotation(), true)) {
									if (data.nbt() != null && data.nbt().contains("metadata")) {
										var value = data.nbt().getString("metadata");
										DataBlockRegistry.PARSER_CODEC.parse(JavaOps.INSTANCE, value).resultOrPartial(
												METAcraftDungeons.LOGGER::error
										).ifPresent(dataBlock -> {
											dataBlock.initialise(this, parameters);
											if (dataBlock instanceof MultiDataBlock multi) {
												multiBlockDataBlocks.add(new DataMultiBlockEntry<>(data.pos(), poolStructurePiece, (DataBlock & MultiDataBlock) multi));
											} else {
												lonelyDataBlocks.add(new DataBlockEntry<>(data.pos(), poolStructurePiece, dataBlock));
											}
										});
									}
								}
							}
						}

						if (shouldThreadStop()) {
							onThreadStop();
							return;
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
									onThreadStop();
									return;
								}
							} catch (InterruptedException ignored) {}
						}

						while (!cache.isEmpty()) {
							if (shouldThreadStop()) {
								onThreadStop();
								return;
							}
							cache.flush(Math.max(MathHelper.floor(25.0 / Math.max(THREAD_COUNT.get(), 1)), 1));
							try {
								Thread.sleep(50);
							} catch (InterruptedException ignored) {}
						}

						if (shouldThreadStop()) {
							onThreadStop();
							return;
						}

						world.getServer().execute(() -> {
							this.targetDim = dungeons.getRegistryKey();

							var dataBlockSets = MultiDataBlock.merge(multiBlockDataBlocks);

							for (var dataBlock : lonelyDataBlocks) {
								dataBlock.datablock.processDataBlock(dataBlock.pos, dataBlock.piece);
							}
							for (var dataBlock : multiBlockDataBlocks) {
								dataBlock.datablock.processDataBlock(dataBlock.pos, dataBlock.piece);
							}
							for (var key : dataBlockSets.keySet()) {
								dataBlockSets.get(key).stream().max(
										Comparator.comparing(entry -> entry.datablock.getPriority())
								).ifPresent(best -> {
									best.datablock.processDataBlocks(dataBlockSets.get(key));
								});
							}

							if (dungeons.getRegistryKey() != this.world.getRegistryKey()) {
								dungeonData.addExternalEntrance(this.world.getRegistryKey(), this.getPos());
							}
							dungeons.getChunkManager().removeTicket(TICKET, averagePos, radius, averagePos);

							((ServerWorld) world).getChunkManager().removeTicket(TICKET, thisPos, 0, thisPos);

							onThreadStop();

							for (var player : playersToNotify) {
								player.sendMessage(Text.literal("The room you wanted to enter is now ready!"), true);
								player.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.BLOCKS, 10, 0.5f);
								TaskScheduler.schedule(world.getServer(), () -> {
									player.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.BLOCKS, 10, 0.75f);
								}, 10);
								TaskScheduler.schedule(world.getServer(), () -> {
									player.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.BLOCKS, 10, 1);
								}, 20);
							}
							playersToNotify.clear();
						});
					});


					world.getServer().execute(() -> {
						((ServerWorld) world).getChunkManager().addTicket(TICKET, thisPos, 0, thisPos);
						dungeons.getChunkManager().addTicket(TICKET, averagePos, radius, averagePos);
						chunkGeneratorThread.start();
					});

				} else {
					METAcraftDungeons.LOGGER.error("Entrance at " + this.pos + " could not generate dungeon.");
				}

			} else {
				METAcraftDungeons.LOGGER.error("Entrance at " + pos + " had no valid dungeon dimension.");
			}
		}
	}

	private boolean isDungeonResetting() {
		var dim = getDungeonDimension();
		if (dim == null) return true;
		return DungeonData.getIfPresent(dim).map(DungeonData::isResetting).orElse(true);
	}

	@Override
	public Entity teleport(Entity entity) {
		if (world == null) return entity;

		if (isDungeonResetting()) {
			if (entity instanceof ServerPlayerEntity player) {
				player.sendMessage(Text.literal("Dungeon dimension still resetting, please wait..."), true);
			}
			return entity;
		}

		if (targetPos == null || chunkGeneratorThread != null) {
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
				player.sendMessage(Text.literal("Dungeon still generating, please wait..."), true);
				playersToNotify.add(player);
			}

			if (chunkGeneratorThread == null) {
				startGenerating();
			}

		} else {
			return super.teleport(entity);
		}
		return entity;
	}

	public void setMaxSize(int maxSize) {
		this.maxSize = maxSize;
		markDirty();
	}

	public void setJigsawPool(RegistryKey<StructurePool> jigsawPool) {
		this.jigsawPool = jigsawPool;
		markDirty();
	}

	public int getMaxSize() {
		return maxSize;
	}

	public RegistryKey<StructurePool> getJigsawPool() {
		return jigsawPool;
	}

	public List<PoolEntry> getDepthSpecificPools() {
		return Collections.unmodifiableList(depthSpecificPools);
	}

	public void setDepthSpecificPools(List<PoolEntry> pools) {
		this.depthSpecificPools = pools;
		markDirty();
	}

	public List<StructurePoolAliasBinding> getAliases() {
		return Collections.unmodifiableList(aliases);
	}

	public void setAliases(List<StructurePoolAliasBinding> aliases) {
		this.aliases = aliases;
		markDirty();
	}

	public void setDepth(int depth) {
		dungeonDepth = depth;
		markDirty();
	}

	public void setDepthOffset(int depthOffset) {
		this.depthOffset = depthOffset;
		markDirty();
	}

	public int getDepth() {
		return dungeonDepth;
	}

	public int getDepthOffset() {
		return depthOffset;
	}

	public static class Parameters {

		public final ServerWorld dungeons;
		public final BlockPos spawnPos;
		public final PoolEntry entry;
		public boolean foundEntrance = false;

		public Parameters(ServerWorld dungeons, BlockPos spawnPos, PoolEntry entry) {
			this.dungeons = dungeons;
			this.spawnPos = spawnPos;
			this.entry = entry;
		}
	}

	public record DataBlockEntry<T extends DataBlock>(BlockPos pos, PoolStructurePiece piece, T datablock) {}

	public record DataMultiBlockEntry<T extends DataBlock & MultiDataBlock>(BlockPos pos, PoolStructurePiece piece, T datablock) {}

	public record PoolEntry(
			RegistryKey<StructurePool> jigsawPool, NumberRange.IntRange depthRange,
			List<StructurePoolAliasBinding> aliases,
			int depthOffset, int maxSize
	) {

		public static final Codec<PoolEntry> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
				RegistryKey.createCodec(RegistryKeys.TEMPLATE_POOL).fieldOf("jigsaw_pool").forGetter(PoolEntry::jigsawPool),
				NumberRange.IntRange.CODEC.fieldOf("depth_range").forGetter(PoolEntry::depthRange),
				ALIAS_BINDING_LIST_CODEC.optionalFieldOf("pool_aliases", List.of()).forGetter(PoolEntry::aliases),
				Codec.INT.fieldOf("depth_offset").forGetter(PoolEntry::depthOffset),
				Codec.INT.fieldOf("max_size").forGetter(PoolEntry::maxSize)
			).apply(instance, PoolEntry::new)
		);
	}
}
