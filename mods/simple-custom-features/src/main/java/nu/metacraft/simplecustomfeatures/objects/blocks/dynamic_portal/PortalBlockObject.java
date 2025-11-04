package nu.metacraft.simplecustomfeatures.objects.blocks.dynamic_portal;

import com.google.common.collect.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.critereon.BlockPredicate;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Position;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.UseRemainder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import nu.metacraft.lib.compat.IsLoaded;
import nu.metacraft.lib.util.helper.StructureTemplateHelper;
import nu.metacraft.simplecustomfeatures.ObjectContainer;
import nu.metacraft.simplecustomfeatures.compat.PortalBlockerCompat;
import nu.metacraft.simplecustomfeatures.compat.PortalTypeData;
import nu.metacraft.simplecustomfeatures.mixin.BlockInWorldAccessor;
import nu.metacraft.simplecustomfeatures.objects.BaseObject;
import nu.metacraft.simplecustomfeatures.objects.ObjectRegistry;
import nu.metacraft.simplecustomfeatures.objects.ObjectType;
import nu.metacraft.simplecustomfeatures.objects.POI;
import nu.metacraft.simplecustomfeatures.objects.blocks.BaseBlock;

import java.util.*;
import java.util.stream.StreamSupport;

public class PortalBlockObject implements BaseBlock {

	private static final Table<ResourceKey<Level>, BlockState, PortalBlockObject> BLOCK_CACHE = HashBasedTable.create();
	private static final Table<ResourceKey<Level>, ItemEntry, PortalBlockObject> ITEM_CACHE = HashBasedTable.create();

	private static final Set<PortalBlockObject> BLOCKS = new LinkedHashSet<>();

	private ResourceKey<PoiType> poiKey;
	private DynamicPortalBlock block;

	private final Map<ResourceKey<Level>, ResourceKey<Level>> dimensions;
	private final BlockPredicate validFrameBlock;
	private final Optional<BlockPredicate> blockActivator;
	private final Optional<BlockPredicate> replaceableByPortal;
	private final Optional<ItemPredicate> itemActivator;
	private final Optional<UseRemainder> useRemainderOverride;
	private final Optional<StructureWithOffset> portalStructure;
	private final Optional<StructureWithOffset> portalWithPlatformStructure;
	private final int minArea;
	private final Map<ResourceKey<Level>, EntitySpawnEntry> entitySpawns;
	private final PortalTypeData portalType;

	private ValidStructureWithOffset defaultPortalStructureCache;
	private ValidStructureWithOffset portalWithPlatformStructureCache;

	public static final MapCodec<PortalBlockObject> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Codec.unboundedMap(Level.RESOURCE_KEY_CODEC, Level.RESOURCE_KEY_CODEC).fieldOf("dimensions").forGetter(p -> p.dimensions),
					BlockPredicate.CODEC.fieldOf("valid_frame_block").forGetter(p -> p.validFrameBlock),
					BlockPredicate.CODEC.optionalFieldOf("block_activator").forGetter(p -> p.blockActivator),
					BlockPredicate.CODEC.optionalFieldOf("replaceable_by_portal").forGetter(p -> p.replaceableByPortal),
					ItemPredicate.CODEC.optionalFieldOf("item_activator").forGetter(p -> p.itemActivator),
					UseRemainder.CODEC.optionalFieldOf("use_remainder_override").forGetter(p -> p.useRemainderOverride),
					StructureWithOffset.CODEC.optionalFieldOf("portal_structure").forGetter(p -> p.portalStructure),
					StructureWithOffset.CODEC.optionalFieldOf("portal_with_platform_structure").forGetter(p -> p.portalWithPlatformStructure),
					ExtraCodecs.POSITIVE_INT.optionalFieldOf("min_area", 1).forGetter(p -> p.minArea),
					Codec.unboundedMap(
							Level.RESOURCE_KEY_CODEC, EntitySpawnEntry.CODEC
					).optionalFieldOf("entity_spawns", Map.of()).forGetter(p -> p.entitySpawns),
					PortalTypeData.CODEC.forGetter(t -> t.portalType)
			).apply(instance, PortalBlockObject::new)
	);

	public PortalBlockObject(
			Map<ResourceKey<Level>, ResourceKey<Level>> dimensions, BlockPredicate validFrameBlock,
			Optional<BlockPredicate> blockActivator, Optional<BlockPredicate> replaceableByPortal,
			Optional<ItemPredicate> itemActivator, Optional<UseRemainder> useRemainderOverride,
			Optional<StructureWithOffset> portalStructure, Optional<StructureWithOffset> portalWithPlatformStructure,
			int minSize,
			Map<ResourceKey<Level>, EntitySpawnEntry> entitySpawns,
			PortalTypeData portalType
	) {
		this.dimensions = dimensions;
		this.validFrameBlock = validFrameBlock;
		this.blockActivator = blockActivator;
		this.replaceableByPortal = replaceableByPortal;
		this.itemActivator = itemActivator;
		this.useRemainderOverride = useRemainderOverride;
		this.portalStructure = portalStructure;
		this.portalWithPlatformStructure = portalWithPlatformStructure;
		this.minArea = minSize;
		this.entitySpawns = entitySpawns;
		this.portalType = portalType;
	}


	@Override
	public ObjectType<? extends BaseObject<Block>, Block> getType() {
		return ObjectRegistry.VERTICAL_PORTAL;
	}

	@Override
	public DataResult<Block> createObject(ResourceKey<Block> id) {
		return DataResult.success(
				block = new DynamicPortalBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.NETHER_PORTAL).setId(id), this)
		);
	}

	public DynamicPortalBlock getBlock() {
		return block;
	}

	public Map<ResourceKey<Level>, EntitySpawnEntry> getEntitySpawns() {
		return entitySpawns;
	}

	public int getMinArea() {
		return minArea;
	}

	public BlockPredicate getValidFrameBlock() {
		return validFrameBlock;
	}

	public ResourceKey<Level> getTargetDim(ResourceKey<Level> dim) {
		return dimensions.get(dim);
	}

	public Optional<PortalShape> findPortalShape(
			ServerLevel world, BlockPos pos
	) {
		return block.findPortalShape(world, pos);
	}

	public ResourceKey<PoiType> getPoiKey() {
		return poiKey;
	}

	public Optional<StructureWithOffset> getPortalStructure() {
		return portalStructure;
	}

	public Optional<ValidStructureWithOffset> getPortalStructure(ServerLevel world) {
		return portalStructure.map(structure -> structure.getStructure(world.getStructureManager())).orElseGet(
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

	public Optional<ValidStructureWithOffset> getPortalWithPlatformStructure(ServerLevel world) {
		return portalWithPlatformStructure.map(structure -> structure.getStructure(world.getStructureManager())).orElseGet(
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

	public Optional<UseRemainder> getUseRemainderOverride() {
		return useRemainderOverride;
	}

	public Optional<BlockPredicate> getBlockActivator() {
		return blockActivator;
	}

	public Optional<BlockPredicate> getReplaceableByPortal() {
		return replaceableByPortal;
	}

	public static void spitOut(BlockSource pointer, ItemStack item) {
		Direction direction = pointer.state().getValue(DispenserBlock.FACING);
		Position position = DispenserBlock.getDispensePosition(pointer);
		DefaultDispenseItemBehavior.spawnItem(pointer.level(), item, 6, direction, position);
	}

	public DispenseItemBehavior getDispenserBehaviour(DispenseItemBehavior otherwise) {
		return (pointer, stack) -> {
			Direction facing = pointer.state().getValue(DispenserBlock.FACING);
			return findPortalShape(pointer.level(), pointer.pos().relative(facing)).map(
				portal -> {
					portal.activate(pointer.level());
					if (stack.isDamageableItem()) {
						stack.hurtAndBreak(1, pointer.level(), null, item -> {});
					} else {
						int c = stack.getCount();
						stack.shrink(1);
						var useRemainder = this.getUseRemainderOverride().orElse(stack.get(DataComponents.USE_REMAINDER));
						if (useRemainder != null) {
							return useRemainder.convertIntoRemainder(
									stack, c, false, extra -> spitOut(pointer, extra)
							);
						}
					}
					return stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
				}
			).orElseGet(() -> otherwise.dispense(pointer, stack));
		};
	}

	@Override
	public Multimap<ResourceLocation, BaseObject<?>> createChildren(ObjectContainer.Loaded<Block> container) {
		poiKey = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, container.getID());
		Multimap<ResourceLocation, BaseObject<?>> map = Multimaps.forMap(Map.of(
				container.getID(), new POI(new POI.PolymerPOI(
						ImmutableSet.copyOf(container.getActualObject().getStateDefinition().getPossibleStates()), 0, 1
				))
		));
		if (IsLoaded.PORTAL_BLOCKER.isLoaded()) {
			map = PortalBlockerCompat.addPortalType(map, container.getID(), block, portalType);
		}
		return map;
	}

	@Override
	public Collection<Registry<?>> getChildrenRegistries() {
		var list = List.<Registry<?>>of(BuiltInRegistries.POINT_OF_INTEREST_TYPE);
		if (IsLoaded.PORTAL_BLOCKER.isLoaded()) {
			list = PortalBlockerCompat.addPortalTypeRegistry(list);
		}
		return list;
	}

	@Override
	public void onUnregister(Holder<Block> entry) {
		BLOCKS.remove(this);
		BLOCK_CACHE.values().remove(this);
		ITEM_CACHE.values().remove(this);
		BaseBlock.super.onUnregister(entry);
	}

	@Override
	public void onRegistrationSuccess(Holder.Reference<Block> entry) {
		BLOCKS.add(this);
		BLOCK_CACHE.clear();
		ITEM_CACHE.clear();
		BaseBlock.super.onRegistrationSuccess(entry);
	}

	public boolean isValidWorld(ServerLevel world) {
		return dimensions.containsKey(world.dimension());
	}

	protected List<BlockState> findFrameBlocks(Level world) {
		return StreamSupport.stream(Block.BLOCK_STATE_REGISTRY.spliterator(), false).map(
				state -> {
					var cached = new BlockInWorld(world, BlockPos.ZERO, false);
					var accessor = (BlockInWorldAccessor) cached;
					accessor.setState(state);
					accessor.setCachedEntity(true);
					if (state.hasBlockEntity()) {
						accessor.setEntity(((EntityBlock) state.getBlock()).newBlockEntity(BlockPos.ZERO, state));
					}
					return cached;
				}
		).filter(
				cachedState -> block.isFrameBlock(cachedState)
		).map(BlockInWorld::getState).toList();
	}

	public static Optional<PortalBlockObject> getForItem(ItemStack stack, ServerLevel world) {
		var entry = new ItemEntry(stack);
		return Optional.ofNullable(ITEM_CACHE.row(world.dimension()).computeIfAbsent(entry, e -> {
			return BLOCKS.stream().filter(block -> block.isValidWorld(world) && block.getItemActivator().map(
					activator -> activator.test(e.stack)
			).orElse(false)).findFirst().orElse(null);
		}));
	}

	public static Optional<PortalBlockObject> getForBlock(BlockPos pos, ServerLevel world, BlockState state) {
		var cache = new BlockInWorld(world, pos, false);
		return Optional.ofNullable(BLOCK_CACHE.row(world.dimension()).computeIfAbsent(state, e -> {
			return BLOCKS.stream().filter(block -> block.isValidWorld(world) && block.getBlockActivator().map(
					activator -> world.hasChunkAt(pos) && activator.matches(cache)
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
				return stack.isEmpty() == o.stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, o.stack);
			}
			return false;
		}

		@Override
		public int hashCode() {
			return ItemStack.hashItemAndComponents(stack);
		}
	}

	public record StructureWithOffset(ResourceLocation id, BlockPos offset) {
		public static final Codec<StructureWithOffset> CODEC = RecordCodecBuilder.create(
				instance -> instance.group(
						ResourceLocation.CODEC.fieldOf("id").forGetter(StructureWithOffset::id),
						BlockPos.CODEC.fieldOf("offset").forGetter(StructureWithOffset::offset)
				).apply(instance, StructureWithOffset::new)
		);
		public Optional<ValidStructureWithOffset> getStructure(StructureTemplateManager manager) {
			return manager.get(id).map(s -> new ValidStructureWithOffset(s, offset));
		}
	}

	public record ValidStructureWithOffset(StructureTemplate structure, BlockPos offset) {
		public static ValidStructureWithOffset createDefault(Level world, PortalBlockObject portal) {
			var defaultBlocks = portal.findFrameBlocks(world);
			if (defaultBlocks.isEmpty()) {
				defaultBlocks = List.of(Blocks.AIR.defaultBlockState());
			}
			StructureTemplate structure = new StructureTemplate();
			final int endX = 3;
			final int endY = 4;
			List<StructureTemplate.StructureBlockInfo> fullBlocks = new ArrayList<>();
			List<StructureTemplate.StructureBlockInfo> blockWithNBT = new ArrayList<>();
			List<StructureTemplate.StructureBlockInfo> otherBlocks = new ArrayList<>();
			for (var pos : BlockPos.betweenClosed(0, 0,0, endX, endY, 0)) {
				BlockState state;
				if (pos.getX() == 0 || pos.getX() == endX || pos.getY() == 0 || pos.getY() == endY) {
					state = defaultBlocks.get(world.getRandom().nextInt(defaultBlocks.size()));
				} else {
					state = portal.block.defaultBlockState().setValue(DynamicPortalBlock.AXIS, Direction.Axis.X);
				}
				StructureTemplate.StructureBlockInfo info = new StructureTemplate.StructureBlockInfo(
						pos.immutable(), state, null
				);
				StructureTemplateHelper.categorize(info, fullBlocks, blockWithNBT, otherBlocks);
			}
			StructureTemplateHelper.setSize(structure, new BlockPos(endX+1, endY+1, 1));
			var blocks = StructureTemplateHelper.combineSorted(fullBlocks, blockWithNBT, otherBlocks);
			StructureTemplateHelper.getBlockInfoLists(structure).add(
					StructureTemplateHelper.createPalettedBlockInfoList(blocks)
			);

			return new ValidStructureWithOffset(structure, new BlockPos(1, 1, 0));
		}

		public ValidStructureWithOffset addPlatformIfNecessary(Level world, PortalBlockObject portal) {
			var structure = new StructureTemplate();
			structure.load(
					world.registryAccess().lookupOrThrow(Registries.BLOCK),
					structure().save(new CompoundTag())
			);
			Direction.Axis axis = null;
			if (structure.getSize().getZ() == 1) {
				axis = Direction.Axis.Z;
			}
			if (structure.getSize().getX() == 1) {
				axis = Direction.Axis.X;
			}
			if (axis != null) {
				var forwardDirection = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
				Set<BlockState> bottomStates = new HashSet<>();

				List<StructureTemplate.StructureBlockInfo> fullBlocks = new ArrayList<>();
				List<StructureTemplate.StructureBlockInfo> blockWithNBT = new ArrayList<>();
				List<StructureTemplate.StructureBlockInfo> otherBlocks = new ArrayList<>();

				int minY = Math.min(offset.getY()-1, 0);
				for (var infos : StructureTemplateHelper.getBlockInfoLists(structure)) {
					for (var info : infos.blocks()) {
						var newInfo = new StructureTemplate.StructureBlockInfo(
								info.pos().relative(forwardDirection),
								info.state(), info.nbt()
						);
						StructureTemplateHelper.categorize(newInfo, fullBlocks, blockWithNBT, otherBlocks);
						if (info.pos().getY() == minY) {
							bottomStates.add(info.state());
						}
					}
				}
				bottomStates.removeIf(
						state -> state.isAir() || state.is(portal.block)
				);
				if (bottomStates.isEmpty()) {
					bottomStates = Set.of(Blocks.AIR.defaultBlockState());
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
						StructureTemplateHelper.categorize(info, fullBlocks, blockWithNBT, otherBlocks);
					}
				}
				StructureTemplateHelper.setSize(structure, structure.getSize().relative(forwardDirection, 2));

				var blocks = StructureTemplateHelper.combineSorted(fullBlocks, blockWithNBT, otherBlocks);
				var list = StructureTemplateHelper.getBlockInfoLists(structure);
				list.clear();
				list.add(
						StructureTemplateHelper.createPalettedBlockInfoList(blocks)
				);
				return new ValidStructureWithOffset(structure, offset.relative(forwardDirection));
			}
			return this;
		}
	}

	public record EntitySpawnEntry(double spawnChance, WeightedList<CompoundTag> entities, boolean initialize) {
		public static final Codec<EntitySpawnEntry> CODEC = RecordCodecBuilder.create(
				instance -> instance.group(
						Codec.doubleRange(0, 1).fieldOf("spawn_chance").forGetter(EntitySpawnEntry::spawnChance),
						Codec.withAlternative(
								WeightedList.codec(CompoundTag.CODEC),
								CompoundTag.CODEC, WeightedList::of
						).fieldOf("entities").forGetter(EntitySpawnEntry::entities),
						Codec.BOOL.fieldOf("initialize").forGetter(EntitySpawnEntry::initialize)
				).apply(instance, EntitySpawnEntry::new)
		);
	}
}
