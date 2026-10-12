package metacraft.pirkko;

import eu.pb4.polymer.core.api.block.PolymerBlock;
import eu.pb4.polymer.virtualentity.api.BlockWithElementHolder;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import eu.pb4.polymer.core.api.item.PolymerItem;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public final class Pirkko implements ModInitializer {
	public static final Identifier ID = Identifier.fromNamespaceAndPath("pirkko", "pirkko");

	@Override
	public void onInitialize() {
		PolymerResourcePackUtils.addModAssets("pirkko");
		PolymerResourcePackUtils.markAsRequired();
		FlowerPotCarrier.register();
		for (var color : DyeColor.values()) {
			register(Identifier.fromNamespaceAndPath("pirkko", "pirkko_" + color.getName()));
		}
		register(Identifier.fromNamespaceAndPath("pirkko", "pirkko_laserviolett"));
		register(Identifier.fromNamespaceAndPath("pirkko", "pirkko_cerise"));
		PirkkoEntity.register();
	}

	private static void register(Identifier id) {
		var block = Registry.register(BuiltInRegistries.BLOCK, id, new PirkkoBlock(id,
				BlockBehaviour.Properties.ofFullCopy(Blocks.FLOWER_POT)
						.strength(0.2F).setId(ResourceKey.create(Registries.BLOCK, id))));
		Registry.register(BuiltInRegistries.ITEM, id, new PirkkoItem(id, block,
				new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id)).useBlockDescriptionPrefix()));
	}

	/** Flower-pot targeting/collision, with the figure rendered by one Polymer item display. */
	private static final class PirkkoBlock extends Block implements PolymerBlock, BlockWithElementHolder {
		private final Identifier modelId;
		private static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;

		private PirkkoBlock(Identifier modelId, Properties properties) {
			super(properties);
			this.modelId = modelId;
			registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.Z));
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			builder.add(AXIS);
		}

		@Override
		public BlockState getStateForPlacement(BlockPlaceContext context) {
			// The colored models' arms run along Z; keep them across the player's view.
			var axis = context.getHorizontalDirection().getAxis() == Direction.Axis.X
					? Direction.Axis.Z : Direction.Axis.X;
			return defaultBlockState().setValue(AXIS, axis);
		}

		@Override
		public BlockState getPolymerBlockState(BlockState state, PacketContext context) {
			return Blocks.FLOWER_POT.defaultBlockState();
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return Blocks.FLOWER_POT.defaultBlockState().getShape(level, pos, context);
		}

		@Override
		public ElementHolder createElementHolder(ServerLevel world, BlockPos pos, BlockState state) {
			var holder = new ElementHolder();
			var stack = new ItemStack(Items.FLOWER_POT);
			stack.set(DataComponents.ITEM_MODEL, modelId);
			var display = new ItemDisplayElement(stack);
			// Two orientations; opposite directions share the same symmetric model.
			display.setItemDisplayContext(ItemDisplayContext.NONE);
			display.setLeftRotation(new org.joml.Quaternionf().rotateY(
					state.getValue(AXIS) == Direction.Axis.Z ? (float) Math.PI : (float) (Math.PI / 2)));
			display.setDisplaySize(1, 1);
			holder.addElement(display);
			return holder;
		}
	}

	private static final class PirkkoItem extends BlockItem implements PolymerItem {
		private final Identifier modelId;
		private PirkkoItem(Identifier modelId, Block block, Properties properties) { super(block, properties); this.modelId = modelId; }

		@Override
		public Item getPolymerItem(ItemStack stack, PacketContext context) { return Items.FLOWER_POT; }

		@Override
		public Identifier getPolymerItemModel(ItemStack stack, PacketContext context, HolderLookup.Provider lookup) {
			return modelId;
		}
	}
}
