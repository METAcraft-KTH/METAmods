package se.metacraft.portable_jukebox.block;

import eu.pb4.polymer.core.api.block.PolymerBlockUtils;
import eu.pb4.polymer.core.api.block.PolymerHeadBlock;
import eu.pb4.polymer.core.api.utils.PolymerUtils;
import eu.pb4.polymer.virtualentity.api.BlockWithElementHolder;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.ClientAsset;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.protocol.Packet;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import se.metacraft.lib.util.EntityRef;
import se.metacraft.portable_jukebox.PortableJukebox;
import se.metacraft.portable_jukebox.item.PortableJukeboxItem;
import se.metacraft.portable_jukebox.entity.PortableJukeboxEntity;
import se.metacraft.portable_jukebox.gui.PortableJukeboxGui;
import se.metacraft.portable_jukebox.mixin.SkullBlockAccessor;
import se.metacraft.portable_jukebox.upgrade.JukeboxUpgrades;

import java.util.List;
import java.util.Optional;

/**
 * A placed portable jukebox. Clients get a player head with a see-through skin, which keeps the
 * head's hitbox, and the jukebox model for its tier is drawn on top with an item display.
 */
public class PortableJukeboxBlock extends BaseEntityBlock implements PolymerHeadBlock, BlockWithElementHolder {

	public static final IntegerProperty ROTATION = SkullBlock.ROTATION;
	/** The jukebox's upgrade tier, kept in the block state so the right model can be shown straight away. */
	public static final IntegerProperty TIER = IntegerProperty.create("tier", 0, JukeboxUpgrades.MAX_TIER);

	private static final Identifier TRANSPARENT_SKIN = PortableJukebox.getID("entity/transparent_skin");

	public PortableJukeboxBlock(Properties settings) {
		super(settings);
		registerDefaultState(stateDefinition.any().setValue(ROTATION, 0).setValue(TIER, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(ROTATION, TIER);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState()
				.setValue(ROTATION, RotationSegment.convertToSegment(context.getRotation()))
				.setValue(TIER, JukeboxUpgrades.getTier(context.getItemInHand()));
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
		return net.minecraft.world.level.block.Blocks.PLAYER_HEAD.defaultBlockState();
	}

	/** The head's skin is see-through: the model is the display, so the head itself must not show. */
	@Override
	public Packet<?> getPolymerHeadPacket(BlockState state, BlockPos pos, PacketContext context) {
		var profile = PolymerUtils.createProfileComponent(PlayerSkin.Patch.create(
				Optional.of(new ClientAsset.ResourceTexture(TRANSPARENT_SKIN)), Optional.empty(), Optional.empty(), Optional.empty()
		));
		var tag = new CompoundTag();
		tag.putString("id", "minecraft:skull");
		tag.put("profile", ResolvableProfile.CODEC.encodeStart(NbtOps.INSTANCE, profile).getOrThrow());
		tag.putInt("x", pos.getX());
		tag.putInt("y", pos.getY());
		tag.putInt("z", pos.getZ());
		return PolymerBlockUtils.createBlockEntityPacket(pos, BlockEntityTypes.SKULL, tag);
	}

	@Override
	public ElementHolder createElementHolder(ServerLevel world, BlockPos pos, BlockState state) {
		var stack = new ItemStack(Items.PAPER);
		stack.set(DataComponents.ITEM_MODEL, PortableJukeboxItem.model(state.getValue(TIER)));
		var display = new ItemDisplayElement(stack);
		display.setItemDisplayContext(ItemDisplayContext.NONE);
		display.setDisplaySize(1, 1);
		// The display is centred on the block, like the model's 16x16x16 grid, so the model's bottom
		// sits on the floor. Its front (the speakers) faces north, so turning it by the placer's yaw
		// points the front back at them.
		float yaw = state.getValue(ROTATION) * 22.5F;
		display.setTransformation(new Matrix4f().rotateY((float) Math.toRadians(-yaw)));
		var holder = new ElementHolder();
		holder.addElement(display);
		return holder;
	}

	@Override
	public String getPolymerSkinValue(BlockState state, BlockPos pos, PacketContext ctx) {
		//From: https://minecraft-heads.com/player-heads/head/3645-jukebox
		return "ewogICJ0aW1lc3RhbXAiIDogMTcxODc5MzE5MzU4NiwKICAicHJvZmlsZUlkIiA6ICIxZjA1NGRlNDgwZmI0NjA0OWE0N2NlMmNiYWE0MjJkMiIsCiAgInByb2ZpbGVOYW1lIiA6ICJQaW5nUG9uZ0RlbGF5IiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2JhZGU4ZjJiYjBhZDVjZDlmNWY5MGRjY2JjYTZlYmU3ZmEzZjk4YTU1OTgyMmNkMGIwNDQ1YzVjNzcyZGJiYTciCiAgICB9CiAgfQp9";
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
		return SkullBlockAccessor.getShape();
	}

	@Override
	protected VoxelShape getOcclusionShape(BlockState state) {
		return Shapes.empty();
	}

	@Override
	public void setPlacedBy(Level world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
		super.setPlacedBy(world, pos, state, placer, itemStack);
		var blockEntity = world.getBlockEntity(pos);
		if (blockEntity instanceof PortableJukeboxBlockEntity b && placer != null) {
			b.setJukebox(itemStack.copy());
			PortableJukeboxEntity.transfer(
					EntityRef.fromEntity(placer), EntityRef.fromBlock(b), b.getJukebox()
			);
		}
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
		var block = world.getBlockEntity(pos);
		if (block instanceof PortableJukeboxBlockEntity jukebox) {
			var gui = PortableJukeboxGui.create((ServerPlayer) player, jukebox.getJukebox(), EntityRef.fromBlock(jukebox));
			gui.open();
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	@Override
	protected List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
		BlockEntity blockEntity = builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
		if (blockEntity instanceof PortableJukeboxBlockEntity jukebox) {
			return List.of(jukebox.getJukebox());
		}
		return super.getDrops(state, builder);
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel world, BlockPos pos, boolean moved) {
		BlockEntity blockEntity = world.getBlockEntity(pos);
		super.affectNeighborsAfterRemoval(state, world, pos, moved);
		if (blockEntity instanceof PortableJukeboxBlockEntity jukebox) {
			world.updateNeighbourForOutputSignal(pos, state.getBlock());
		}
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new PortableJukeboxBlockEntity(pos, state);
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level world, BlockPos pos, Direction direction) {
		var blockEntity = world.getBlockEntity(pos);
		if (blockEntity instanceof PortableJukeboxBlockEntity portable) {
			return PortableJukeboxItem.getComparatorOutput(portable.getJukebox());
		}
		return 0;
	}

	@Override
	protected void neighborChanged(
			BlockState state, Level world, BlockPos pos, Block sourceBlock,
			Orientation orientation, boolean notify
	) {
		var blockEntity = world.getBlockEntity(pos);
		if (blockEntity instanceof PortableJukeboxBlockEntity portable) {
			PortableJukeboxItem.updateRedstone(EntityRef.fromBlock(portable), portable.getJukebox());
		}
	}
}
