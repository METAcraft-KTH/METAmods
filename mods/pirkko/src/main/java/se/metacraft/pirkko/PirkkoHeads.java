package se.metacraft.pirkko;

import eu.pb4.polymer.core.api.block.PolymerHeadBlock;
import eu.pb4.polymer.core.api.item.PolymerItem;
import eu.pb4.polymer.core.api.utils.PolymerUtils;
import eu.pb4.polymer.virtualentity.api.BlockWithElementHolder;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.ClientAsset;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.WallSkullBlock;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import eu.pb4.polymer.core.api.block.PolymerBlockUtils;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Pirkko heads: real server-side blocks that Polymer shows as player heads with a fully transparent skin,
 * with the figure shown by an item display inside. There is a standing and a wall block per variant,
 * placed by one item like vanilla heads.
 */
public final class PirkkoHeads {
	/** Colors shared by mobs and heads; each has a {@code pirkko_<color>} model and skin. */
	public static final List<String> COLORS = new ArrayList<>();
	/** The colors plus the animated rainbow. */
	public static final List<String> VARIANTS = new ArrayList<>();
	static {
		for (var color : DyeColor.values()) COLORS.add(color.getName());
		COLORS.add("laserviolett");
		COLORS.add("cerise");
		VARIANTS.addAll(COLORS);
		VARIANTS.add("rainbow");
	}

	static void register() {
		for (var variant : VARIANTS) {
			var id = id(variant);
			var floor = Registry.register(BuiltInRegistries.BLOCK, id, new Standing(variant,
					properties(id).strength(1.0F)));
			var wall = Registry.register(BuiltInRegistries.BLOCK, wallId(variant), new Wall(variant,
					properties(wallId(variant)).strength(1.0F).overrideLootTable(floor.getLootTable())));
			Registry.register(BuiltInRegistries.ITEM, id, new HeadItem(variant, floor, wall,
					new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id))));
		}
	}

	private static BlockBehaviour.Properties properties(Identifier id) {
		return BlockBehaviour.Properties.ofFullCopy(Blocks.PLAYER_HEAD).setId(ResourceKey.create(Registries.BLOCK, id));
	}

	public static Identifier id(String variant) {
		return Identifier.fromNamespaceAndPath("pirkko", "pirkko_" + variant);
	}

	private static Identifier wallId(String variant) {
		return Identifier.fromNamespaceAndPath("pirkko", "pirkko_" + variant + "_wall");
	}

	/** The head item for a variant. */
	public static ItemStack stack(String variant) {
		return new ItemStack(BuiltInRegistries.ITEM.getValue(id(variant)));
	}

	/** The transparent skin; the figure is the display inside, so the head itself must not show. */
	private static Packet<?> skinPacket(BlockPos pos, String variant) {
		var skin = Identifier.fromNamespaceAndPath("pirkko", "entity/pirkko_" + variant);
		var profile = PolymerUtils.createProfileComponent(PlayerSkin.Patch.create(
				Optional.of(new ClientAsset.ResourceTexture(skin)), Optional.empty(), Optional.empty(), Optional.empty()));
		var tag = new CompoundTag();
		tag.putString("id", "minecraft:skull");
		tag.put("profile", ResolvableProfile.CODEC.encodeStart(NbtOps.INSTANCE, profile).getOrThrow());
		tag.putInt("x", pos.getX());
		tag.putInt("y", pos.getY());
		tag.putInt("z", pos.getZ());
		return PolymerBlockUtils.createBlockEntityPacket(pos, BlockEntityTypes.SKULL, tag);
	}

	private static ElementHolder holder(String variant, Matrix4f placement) {
		var stack = new ItemStack(Items.PAPER);
		stack.set(DataComponents.ITEM_MODEL, id(variant));
		var display = new ItemDisplayElement(stack);
		display.setItemDisplayContext(ItemDisplayContext.NONE);
		display.setDisplaySize(1, 1);
		// Item-display geometry is centered on (8,8,8); the 8 px figure fills the head cube, so its middle
		// (4 px up from its base) goes to the cube's center. `placement` is relative to the block center.
		display.setTransformation(placement.translate(0, 0.25F, 0));
		var holder = new ElementHolder();
		holder.addElement(display);
		return holder;
	}

	private static final class Standing extends Block implements PolymerHeadBlock, BlockWithElementHolder {
		static final IntegerProperty ROTATION = SkullBlock.ROTATION;
		private final String variant;

		private Standing(String variant, Properties properties) {
			super(properties);
			this.variant = variant;
			registerDefaultState(stateDefinition.any().setValue(ROTATION, 0));
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			builder.add(ROTATION);
		}

		@Override
		public BlockState getStateForPlacement(BlockPlaceContext context) {
			return defaultBlockState().setValue(ROTATION, RotationSegment.convertToSegment(context.getRotation()));
		}

		@Override
		protected BlockState rotate(BlockState state, Rotation rotation) {
			return state.setValue(ROTATION, rotation.rotate(state.getValue(ROTATION), 16));
		}

		@Override
		protected BlockState mirror(BlockState state, Mirror mirror) {
			return state.setValue(ROTATION, mirror.mirror(state.getValue(ROTATION), 16));
		}

		@Override
		public BlockState getPolymerBlockState(BlockState state, PacketContext context) {
			return Blocks.PLAYER_HEAD.defaultBlockState().setValue(SkullBlock.ROTATION, state.getValue(ROTATION));
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return Blocks.PLAYER_HEAD.defaultBlockState().getShape(level, pos, context);
		}

		@Override
		public String getPolymerSkinValue(BlockState state, BlockPos pos, PacketContext context) { return ""; }

		@Override
		public Packet<?> getPolymerHeadPacket(BlockState state, BlockPos pos, PacketContext context) {
			return skinPacket(pos, variant);
		}

		@Override
		public ElementHolder createElementHolder(ServerLevel world, BlockPos pos, BlockState state) {
			// The head cube sits on the block floor. Turn the model's Z-aligned arms across the head's front.
			float yaw = state.getValue(ROTATION) * 22.5F;
			return holder(variant, new Matrix4f().translate(0, -0.25F, 0)
					.rotateY((float) Math.toRadians(-yaw)).rotateY((float) Math.PI / 2));
		}
	}

	private static final class Wall extends Block implements PolymerHeadBlock, BlockWithElementHolder {
		static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
		private final String variant;

		private Wall(String variant, Properties properties) {
			super(properties);
			this.variant = variant;
			registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			builder.add(FACING);
		}

		@Override
		public BlockState getStateForPlacement(BlockPlaceContext context) {
			// Same rule as vanilla wall heads: the nearest looking direction with something behind it.
			for (var direction : context.getNearestLookingDirections()) {
				if (!direction.getAxis().isHorizontal()) continue;
				var state = defaultBlockState().setValue(FACING, direction.getOpposite());
				if (!context.getLevel().getBlockState(context.getClickedPos().relative(direction)).canBeReplaced(context)) {
					return state;
				}
			}
			return null;
		}

		@Override
		protected BlockState rotate(BlockState state, Rotation rotation) {
			return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
		}

		@Override
		protected BlockState mirror(BlockState state, Mirror mirror) {
			return state.rotate(mirror.getRotation(state.getValue(FACING)));
		}

		@Override
		public BlockState getPolymerBlockState(BlockState state, PacketContext context) {
			return Blocks.PLAYER_WALL_HEAD.defaultBlockState().setValue(WallSkullBlock.FACING, state.getValue(FACING));
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return getPolymerBlockState(state, null).getShape(level, pos, context);
		}

		@Override
		public String getPolymerSkinValue(BlockState state, BlockPos pos, PacketContext context) { return ""; }

		@Override
		public Packet<?> getPolymerHeadPacket(BlockState state, BlockPos pos, PacketContext context) {
			return skinPacket(pos, variant);
		}

		@Override
		public ElementHolder createElementHolder(ServerLevel world, BlockPos pos, BlockState state) {
			// The figure's feet are on the wall and its front points away from it; the arms stay horizontal.
			var facing = state.getValue(FACING);
			var up = new Vector3f(facing.getStepX(), facing.getStepY(), facing.getStepZ());
			var z = new Vector3f(0, 1, 0).cross(up).normalize();
			var x = new Vector3f(up).cross(z);
			return holder(variant, new Matrix4f().translate(-up.x * 0.25F, -up.y * 0.25F, -up.z * 0.25F)
					.mul(new Matrix4f(new Matrix3f(x, up, z))));
		}
	}

	private static final class HeadItem extends StandingAndWallBlockItem implements PolymerItem {
		private final String variant;

		private HeadItem(String variant, Block floor, Block wall, Properties properties) {
			super(floor, wall, Direction.DOWN, properties);
			this.variant = variant;
		}

		@Override
		public Item getPolymerItem(ItemStack stack, PacketContext context) { return Items.PLAYER_HEAD; }

		@Override
		public Identifier getPolymerItemModel(ItemStack stack, PacketContext context, HolderLookup.Provider lookup) {
			// A transparent head would be an empty inventory slot, so show the flat icon instead.
			return Identifier.fromNamespaceAndPath("pirkko", "head_" + variant);
		}
	}

	private PirkkoHeads() {}
}
