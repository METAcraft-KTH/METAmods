package se.datasektionen.mc.simplecustomfeatures.objects.blocks.dynamic_portal;

import com.google.common.collect.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.*;
import net.minecraft.block.dispenser.DispenserBehavior;
import net.minecraft.block.pattern.CachedBlockPosition;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.predicate.BlockPredicate;
import net.minecraft.predicate.item.ItemPredicate;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DataPool;
import net.minecraft.util.dynamic.Codecs;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.*;
import net.minecraft.world.poi.PointOfInterestType;
import se.datasektionen.mc.simplecustomfeatures.ObjectContainer;
import se.datasektionen.mc.simplecustomfeatures.mixin.AccessorCachedBlockPosition;
import se.datasektionen.mc.simplecustomfeatures.mixin.AccessorStructureTemplate;
import se.datasektionen.mc.simplecustomfeatures.objects.BaseObject;
import se.datasektionen.mc.simplecustomfeatures.objects.ObjectRegistry;
import se.datasektionen.mc.simplecustomfeatures.objects.ObjectType;
import se.datasektionen.mc.simplecustomfeatures.objects.POI;
import se.datasektionen.mc.simplecustomfeatures.objects.blocks.BaseBlock;

import java.util.*;
import java.util.stream.StreamSupport;

public class PortalBlockObject implements BaseBlock {

	private static final Table<RegistryKey<World>, BlockState, PortalBlockObject> BLOCK_CACHE = HashBasedTable.create();
	private static final Table<RegistryKey<World>, ItemEntry, PortalBlockObject> ITEM_CACHE = HashBasedTable.create();

	private static final Set<PortalBlockObject> BLOCKS = new LinkedHashSet<>();

	private RegistryKey<PointOfInterestType> poiKey;
	private DynamicPortalBlock block;

	private final Map<RegistryKey<World>, RegistryKey<World>> dimensions;
	private final BlockPredicate validFrameBlock;
	private final Optional<BlockPredicate> blockActivator;
	private final Optional<ItemPredicate> itemActivator;
	private final Optional<StructureWithOffset> portalStructure;
	private final Optional<StructureWithOffset> portalWithPlatformStructure;
	private final int minArea;
	private final Map<RegistryKey<World>, EntitySpawnEntry> entitySpawns;

	private ValidStructureWithOffset defaultPortalStructureCache;
	private ValidStructureWithOffset portalWithPlatformStructureCache;

	public static final MapCodec<PortalBlockObject> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Codec.unboundedMap(World.CODEC, World.CODEC).fieldOf("dimensions").forGetter(p -> p.dimensions),
					BlockPredicate.CODEC.fieldOf("valid_frame_block").forGetter(p -> p.validFrameBlock),
					BlockPredicate.CODEC.optionalFieldOf("block_activator").forGetter(p -> p.blockActivator),
					ItemPredicate.CODEC.optionalFieldOf("item_activator").forGetter(p -> p.itemActivator),
					StructureWithOffset.CODEC.optionalFieldOf("portal_structure").forGetter(p -> p.portalStructure),
					StructureWithOffset.CODEC.optionalFieldOf("portal_with_platform_structure").forGetter(p -> p.portalWithPlatformStructure),
					Codecs.POSITIVE_INT.optionalFieldOf("min_area", 1).forGetter(p -> p.minArea),
					Codec.unboundedMap(
							World.CODEC, EntitySpawnEntry.CODEC
					).optionalFieldOf("entity_spawns", Map.of()).forGetter(p -> p.entitySpawns)
			).apply(instance, PortalBlockObject::new)
	);

	public PortalBlockObject(
			Map<RegistryKey<World>, RegistryKey<World>> dimensions, BlockPredicate validFrameBlock,
			Optional<BlockPredicate> blockActivator, Optional<ItemPredicate> itemActivator,
			Optional<StructureWithOffset> portalStructure, Optional<StructureWithOffset> portalWithPlatformStructure,
			int minSize,
			Map<RegistryKey<World>, EntitySpawnEntry> entitySpawns
	) {
		this.dimensions = dimensions;
		this.validFrameBlock = validFrameBlock;
		this.blockActivator = blockActivator;
		this.itemActivator = itemActivator;
		this.portalStructure = portalStructure;
		this.portalWithPlatformStructure = portalWithPlatformStructure;
		this.minArea = minSize;
		this.entitySpawns = entitySpawns;
	}


	@Override
	public ObjectType<? extends BaseObject<Block>, Block> getType() {
		return ObjectRegistry.VERTICAL_PORTAL;
	}

	@Override
	public DataResult<Block> createObject() {
		return DataResult.success(
				block = new DynamicPortalBlock(AbstractBlock.Settings.copy(Blocks.NETHER_PORTAL), this)
		);
	}

	public Map<RegistryKey<World>, EntitySpawnEntry> getEntitySpawns() {
		return entitySpawns;
	}

	public int getMinArea() {
		return minArea;
	}

	public BlockPredicate getValidFrameBlock() {
		return validFrameBlock;
	}

	public RegistryKey<World> getTargetDim(RegistryKey<World> dim) {
		return dimensions.get(dim);
	}

	public Optional<PortalShape> findPortalShape(
			ServerWorld world, BlockPos pos
	) {
		return block.findPortalShape(world, pos);
	}

	public RegistryKey<PointOfInterestType> getPoiKey() {
		return poiKey;
	}

	public Optional<StructureWithOffset> getPortalStructure() {
		return portalStructure;
	}

	public Optional<ValidStructureWithOffset> getPortalStructure(ServerWorld world) {
		return portalStructure.map(structure -> structure.getStructure(world.getStructureTemplateManager())).orElseGet(
				() -> {
					if (defaultPortalStructureCache == null) {
						defaultPortalStructureCache = ValidStructureWithOffset.createDefault(
								world, this
						);
					}
					return Optional.of(defaultPortalStructureCache);
				}
		);
	}

	public Optional<StructureWithOffset> getPortalWithPlatformStructure() {
		return portalWithPlatformStructure;
	}

	public Optional<ValidStructureWithOffset> getPortalWithPlatformStructure(ServerWorld world) {
		return portalWithPlatformStructure.map(structure -> structure.getStructure(world.getStructureTemplateManager())).orElseGet(
				() -> {
					if (portalWithPlatformStructureCache == null) {
						portalWithPlatformStructureCache = getPortalStructure(world).map(
								portal -> portal.addPlatformIfNecessary(
										world, this
								)
						).orElse(null);
					}
					return Optional.ofNullable(portalWithPlatformStructureCache);
				}
		);
	}

	public Optional<ItemPredicate> getItemActivator() {
		return itemActivator;
	}

	public Optional<BlockPredicate> getBlockActivator() {
		return blockActivator;
	}

	public DispenserBehavior getDispenserBehaviour(DispenserBehavior otherwise) {
		return (pointer, stack) -> {
			Direction facing = pointer.state().get(DispenserBlock.FACING);
			return findPortalShape(pointer.world(), pointer.pos().offset(facing)).map(
				portal -> {
					portal.activate();
					if (stack.isDamageable()) {
						stack.damage(1, pointer.world(), null, item -> {});
					} else {
						stack.decrement(1);
					}
					return stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
				}
			).orElse(otherwise.dispense(pointer, stack));
		};
	}

	@Override
	public Multimap<Identifier, BaseObject<?>> createChildren(ObjectContainer.Loaded<Block> container) {
		poiKey = RegistryKey.of(RegistryKeys.POINT_OF_INTEREST_TYPE, container.getID());
		return Multimaps.forMap(Map.of(
				container.getID(), new POI(new PointOfInterestType(
						ImmutableSet.copyOf(container.getActualObject().getStateManager().getStates()), 0, 1
				))
		));
	}

	@Override
	public Collection<Registry<?>> getChildrenRegistries() {
		return List.of(Registries.POINT_OF_INTEREST_TYPE);
	}

	@Override
	public void onUnregister(RegistryEntry<Block> entry) {
		BLOCKS.remove(this);
		BLOCK_CACHE.values().remove(this);
		ITEM_CACHE.values().remove(this);
		BaseBlock.super.onUnregister(entry);
	}

	@Override
	public void onRegistrationSuccess(RegistryEntry.Reference<Block> entry) {
		BLOCKS.add(this);
		BLOCK_CACHE.clear();
		ITEM_CACHE.clear();
		BaseBlock.super.onRegistrationSuccess(entry);
	}

	public boolean isValidWorld(ServerWorld world) {
		return dimensions.containsKey(world.getRegistryKey());
	}

	protected List<BlockState> findFrameBlocks(World world) {
		return StreamSupport.stream(Block.STATE_IDS.spliterator(), false).map(
				state -> {
					var cached = new CachedBlockPosition(world, BlockPos.ORIGIN, false);
					var accessor = (AccessorCachedBlockPosition) cached;
					accessor.setState(state);
					accessor.setCachedEntity(true);
					if (state.hasBlockEntity()) {
						accessor.setBlockEntity(((BlockEntityProvider) state.getBlock()).createBlockEntity(BlockPos.ORIGIN, state));
					}
					return cached;
				}
		).filter(
				cachedState -> block.isFrameBlock(cachedState)
		).map(CachedBlockPosition::getBlockState).toList();
	}

	public static Optional<PortalBlockObject> getForItem(ItemStack stack, ServerWorld world) {
		var entry = new ItemEntry(stack);
		return Optional.ofNullable(ITEM_CACHE.row(world.getRegistryKey()).computeIfAbsent(entry, e -> {
			return BLOCKS.stream().filter(block -> block.isValidWorld(world) && block.getItemActivator().map(
					activator -> activator.test(e.stack)
			).orElse(false)).findFirst().orElse(null);
		}));
	}

	public static Optional<PortalBlockObject> getForBlock(BlockPos pos, ServerWorld world, BlockState state) {
		var cache = new CachedBlockPosition(world, pos, false);
		return Optional.ofNullable(BLOCK_CACHE.row(world.getRegistryKey()).computeIfAbsent(state, e -> {
			return BLOCKS.stream().filter(block -> block.isValidWorld(world) && block.getBlockActivator().map(
					activator -> activator.test(cache)
			).orElse(false)).findFirst().orElse(null);
		}));
	}

	public static class ItemEntry {
		private final ItemStack stack;

		public ItemEntry(ItemStack stack) {
			this.stack = stack;
		}

		@Override
		public boolean equals(Object other) {
			if (other == null) {
				return stack == null;
			}
			if (!other.getClass().equals(this.getClass())) return false;
			ItemEntry o = (ItemEntry) other;
			if (stack == o.stack) return true;
			if (stack != null && o.stack != null) {
				return stack.isEmpty() == o.stack.isEmpty() && ItemStack.areItemsAndComponentsEqual(stack, o.stack);
			}
			return false;
		}

		@Override
		public int hashCode() {
			return ItemStack.hashCode(stack);
		}
	}

	public record StructureWithOffset(Identifier id, BlockPos offset) {
		public static final Codec<StructureWithOffset> CODEC = RecordCodecBuilder.create(
				instance -> instance.group(
						Identifier.CODEC.fieldOf("id").forGetter(StructureWithOffset::id),
						BlockPos.CODEC.fieldOf("offset").forGetter(StructureWithOffset::offset)
				).apply(instance, StructureWithOffset::new)
		);
		public Optional<ValidStructureWithOffset> getStructure(StructureTemplateManager manager) {
			return manager.getTemplate(id).map(s -> new ValidStructureWithOffset(s, offset));
		}
	}

	public record ValidStructureWithOffset(StructureTemplate structure, BlockPos offset) {
		public static ValidStructureWithOffset createDefault(World world, PortalBlockObject portal) {
			var defaultBlocks = portal.findFrameBlocks(world);
			if (defaultBlocks.isEmpty()) {
				defaultBlocks = List.of(Blocks.AIR.getDefaultState());
			}
			StructureTemplate structure = new StructureTemplate();
			final int endX = 3;
			final int endY = 4;
			List<StructureTemplate.StructureBlockInfo> fullBlocks = new ArrayList<>();
			List<StructureTemplate.StructureBlockInfo> blockWithNBT = new ArrayList<>();
			List<StructureTemplate.StructureBlockInfo> otherBlocks = new ArrayList<>();
			for (var pos : BlockPos.iterate(0, 0,0, endX, endY, 0)) {
				BlockState state;
				if (pos.getX() == 0 || pos.getX() == endX || pos.getY() == 0 || pos.getY() == endY) {
					state = defaultBlocks.get(world.getRandom().nextInt(defaultBlocks.size()));
				} else {
					state = portal.block.getDefaultState().with(DynamicPortalBlock.AXIS, Direction.Axis.X);
				}
				StructureTemplate.StructureBlockInfo info = new StructureTemplate.StructureBlockInfo(
						pos.toImmutable(), state, null
				);
				AccessorStructureTemplate.callCategorize(info, fullBlocks, blockWithNBT, otherBlocks);
			}
			AccessorStructureTemplate accessor = (AccessorStructureTemplate) structure;
			accessor.setSize(new BlockPos(endX+1, endY+1, 1));
			var blocks = AccessorStructureTemplate.callCombineSorted(fullBlocks, blockWithNBT, otherBlocks);
			accessor.getBlockInfoLists().add(
					AccessorStructureTemplate.AccessorPalettedBlockInfoList.init(blocks)
			);

			return new ValidStructureWithOffset(structure, new BlockPos(1, 1, 0));
		}

		public ValidStructureWithOffset addPlatformIfNecessary(World world, PortalBlockObject portal) {
			var structure = new StructureTemplate();
			structure.readNbt(
					world.getRegistryManager().getWrapperOrThrow(RegistryKeys.BLOCK),
					structure().writeNbt(new NbtCompound())
			);
			Direction.Axis axis = null;
			if (structure.getSize().getZ() == 1) {
				axis = Direction.Axis.Z;
			}
			if (structure.getSize().getX() == 1) {
				axis = Direction.Axis.X;
			}
			if (axis != null) {
				var forwardDirection = Direction.from(axis, Direction.AxisDirection.POSITIVE);
				AccessorStructureTemplate accessor = (AccessorStructureTemplate) structure;
				Set<BlockState> bottomStates = new HashSet<>();

				List<StructureTemplate.StructureBlockInfo> fullBlocks = new ArrayList<>();
				List<StructureTemplate.StructureBlockInfo> blockWithNBT = new ArrayList<>();
				List<StructureTemplate.StructureBlockInfo> otherBlocks = new ArrayList<>();

				int minY = Math.min(offset.getY()-1, 0);
				for (var infos : accessor.getBlockInfoLists()) {
					for (var info : infos.getAll()) {
						var newInfo = new StructureTemplate.StructureBlockInfo(
								info.pos().offset(forwardDirection),
								info.state(), info.nbt()
						);
						AccessorStructureTemplate.callCategorize(newInfo, fullBlocks, blockWithNBT, otherBlocks);
						if (info.pos().getY() == minY) {
							bottomStates.add(info.state());
						}
					}
				}
				bottomStates.removeIf(
						state -> state.isAir() || state.isOf(portal.block)
				);
				if (bottomStates.isEmpty()) {
					bottomStates = Set.of(Blocks.AIR.getDefaultState());
				}
				List<BlockState> bottomStateList = new ArrayList<>(bottomStates);

				int maxWidth = switch (axis) {
					case X -> structure.getSize().getZ();
					case Z -> structure.getSize().getX();
					case Y -> throw new IllegalStateException("How is this y?");
				};
				for (int width = 1; width < maxWidth-1; width++) {
					for (int forward = 0; forward < 3; forward+=2) {
						var info = new StructureTemplate.StructureBlockInfo(
								switch (axis) {
									case X -> new BlockPos(forward, minY, width);
									case Z -> new BlockPos(width, minY, forward);
									case Y -> throw new IllegalStateException("How is this y?");
								},
								bottomStateList.get(world.getRandom().nextInt(bottomStateList.size())), null
						);
						AccessorStructureTemplate.callCategorize(info, fullBlocks, blockWithNBT, otherBlocks);
					}
				}
				accessor.setSize(structure.getSize().offset(forwardDirection, 2));

				var blocks = AccessorStructureTemplate.callCombineSorted(fullBlocks, blockWithNBT, otherBlocks);
				accessor.getBlockInfoLists().clear();
				accessor.getBlockInfoLists().add(
						AccessorStructureTemplate.AccessorPalettedBlockInfoList.init(blocks)
				);
				return new ValidStructureWithOffset(structure, offset.offset(forwardDirection));
			}
			return this;
		}
	}

	public record EntitySpawnEntry(double spawnChance, DataPool<NbtCompound> entities, boolean initialize) {
		public static final Codec<EntitySpawnEntry> CODEC = RecordCodecBuilder.create(
				instance -> instance.group(
						Codec.doubleRange(0, 1).fieldOf("spawn_chance").forGetter(EntitySpawnEntry::spawnChance),
						Codec.withAlternative(
								DataPool.createCodec(NbtCompound.CODEC),
								NbtCompound.CODEC, DataPool::of
						).fieldOf("entities").forGetter(EntitySpawnEntry::entities),
						Codec.BOOL.fieldOf("initialize").forGetter(EntitySpawnEntry::initialize)
				).apply(instance, EntitySpawnEntry::new)
		);
	}
}
