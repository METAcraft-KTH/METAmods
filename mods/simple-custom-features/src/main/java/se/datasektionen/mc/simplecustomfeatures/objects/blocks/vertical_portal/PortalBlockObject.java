package se.datasektionen.mc.simplecustomfeatures.objects.blocks.vertical_portal;

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
import se.datasektionen.mc.simplecustomfeatures.RegistryHelper;
import se.datasektionen.mc.simplecustomfeatures.objects.BaseObject;
import se.datasektionen.mc.simplecustomfeatures.objects.ObjectRegistry;
import se.datasektionen.mc.simplecustomfeatures.objects.ObjectType;
import se.datasektionen.mc.simplecustomfeatures.objects.POI;

import java.util.*;
import java.util.List;

public class PortalBlockObject implements BaseObject<Block> {

	private static final Table<RegistryKey<World>, BlockState, PortalBlockObject> BLOCK_CACHE = HashBasedTable.create();
	private static final Table<RegistryKey<World>, ItemEntry, PortalBlockObject> ITEM_CACHE = HashBasedTable.create();

	private static final Set<PortalBlockObject> BLOCKS = new LinkedHashSet<>();

	private RegistryKey<PointOfInterestType> poiKey;
	private VerticalPortalBlock block;

	private final Map<RegistryKey<World>, RegistryKey<World>> dimensions;
	private final BlockPredicate validFrameBlock;
	private final Optional<BlockPredicate> blockActivator;
	private final Optional<ItemPredicate> itemActivator;
	private final StructureWithOffset normalPortal;
	private final StructureWithOffset portalWithPlatform;
	private final int minArea;
	private final Map<RegistryKey<World>, EntitySpawnEntry> entitySpawns;

	public static final MapCodec<PortalBlockObject> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Codec.unboundedMap(World.CODEC, World.CODEC).fieldOf("dimensions").forGetter(p -> p.dimensions),
					BlockPredicate.CODEC.fieldOf("valid_frame_block").forGetter(p -> p.validFrameBlock),
					BlockPredicate.CODEC.optionalFieldOf("block_activator").forGetter(p -> p.blockActivator),
					ItemPredicate.CODEC.optionalFieldOf("item_activator").forGetter(p -> p.itemActivator),
					StructureWithOffset.CODEC.fieldOf("normal_portal").forGetter(p -> p.normalPortal),
					StructureWithOffset.CODEC.fieldOf("portal_with_platform").forGetter(p -> p.portalWithPlatform),
					Codecs.POSITIVE_INT.optionalFieldOf("min_area", 1).forGetter(p -> p.minArea),
					Codec.unboundedMap(
							World.CODEC, EntitySpawnEntry.CODEC
					).optionalFieldOf("entity_spawns", Map.of()).forGetter(p -> p.entitySpawns)
			).apply(instance, PortalBlockObject::new)
	);

	public PortalBlockObject(
			Map<RegistryKey<World>, RegistryKey<World>> dimensions, BlockPredicate validFrameBlock,
			Optional<BlockPredicate> blockActivator, Optional<ItemPredicate> itemActivator,
			StructureWithOffset normalPortal, StructureWithOffset portalWithPlatform,
			int minSize,
			Map<RegistryKey<World>, EntitySpawnEntry> entitySpawns
	) {
		this.dimensions = dimensions;
		this.validFrameBlock = validFrameBlock;
		this.blockActivator = blockActivator;
		this.itemActivator = itemActivator;
		this.normalPortal = normalPortal;
		this.portalWithPlatform = portalWithPlatform;
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
				block = new VerticalPortalBlock(AbstractBlock.Settings.copy(Blocks.NETHER_PORTAL), this)
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

	public StructureWithOffset getNormalPortal() {
		return normalPortal;
	}

	public StructureWithOffset getPortalWithPlatform() {
		return portalWithPlatform;
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
	public void onRegistrationFail(Block value) {
		RegistryHelper.removeIntrusiveEntry(Registries.BLOCK, value);
	}

	@Override
	public void onUnregister(RegistryEntry<Block> entry) {
		BLOCKS.remove(this);
		BLOCK_CACHE.values().remove(this);
		ITEM_CACHE.values().remove(this);
		RegistryHelper.removeBlockStatesFor(entry.value());
	}

	@Override
	public void onRegistrationSuccess(RegistryEntry.Reference<Block> entry) {
		BLOCKS.add(this);
		BLOCK_CACHE.clear();
		ITEM_CACHE.clear();
		RegistryHelper.finishBlockRegistration(entry.value());
	}

	public boolean isValidWorld(ServerWorld world) {
		return dimensions.containsKey(world.getRegistryKey());
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
