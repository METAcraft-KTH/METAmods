package nu.metacraft.portable_jukebox.block;

import com.mojang.serialization.MapCodec;
import eu.pb4.polymer.core.api.block.PolymerHeadBlock;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootWorldContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.block.WireOrientation;
import org.jetbrains.annotations.Nullable;
import nu.metacraft.lib.util.EntityRef;
import nu.metacraft.portable_jukebox.item.PortableJukeboxItem;
import nu.metacraft.portable_jukebox.entity.PortableJukeboxEntity;
import nu.metacraft.portable_jukebox.gui.PortableJukeboxGui;
import nu.metacraft.portable_jukebox.mixin.AccessorSkullBlock;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.List;

public class PortableJukeboxBlock extends BlockWithEntity implements PolymerHeadBlock {

	public static final MapCodec<PortableJukeboxBlock> CODEC = EnderChestBlock.createCodec(PortableJukeboxBlock::new);

	public PortableJukeboxBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	public String getPolymerSkinValue(BlockState state, BlockPos pos, PacketContext ctx) {
		//From: https://minecraft-heads.com/player-heads/head/3645-jukebox
		return "ewogICJ0aW1lc3RhbXAiIDogMTcxODc5MzE5MzU4NiwKICAicHJvZmlsZUlkIiA6ICIxZjA1NGRlNDgwZmI0NjA0OWE0N2NlMmNiYWE0MjJkMiIsCiAgInByb2ZpbGVOYW1lIiA6ICJQaW5nUG9uZ0RlbGF5IiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2JhZGU4ZjJiYjBhZDVjZDlmNWY5MGRjY2JjYTZlYmU3ZmEzZjk4YTU1OTgyMmNkMGIwNDQ1YzVjNzcyZGJiYTciCiAgICB9CiAgfQp9";
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return AccessorSkullBlock.getShape();
	}

	@Override
	protected VoxelShape getCullingShape(BlockState state) {
		return VoxelShapes.empty();
	}

	@Override
	public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
		super.onPlaced(world, pos, state, placer, itemStack);
		var blockEntity = world.getBlockEntity(pos);
		if (blockEntity instanceof PortableJukeboxBlockEntity b && placer != null) {
			b.setJukebox(itemStack.copy());
			PortableJukeboxEntity.transfer(
					EntityRef.fromEntity(placer), EntityRef.fromBlock(b), b.getJukebox()
			);
		}
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		var block = world.getBlockEntity(pos);
		if (block instanceof PortableJukeboxBlockEntity jukebox) {
			var gui = PortableJukeboxGui.create((ServerPlayerEntity) player, jukebox.getJukebox(), EntityRef.fromBlock(jukebox));
			gui.open();
			return ActionResult.SUCCESS;
		}
		return ActionResult.PASS;
	}

	@Override
	protected List<ItemStack> getDroppedStacks(BlockState state, LootWorldContext.Builder builder) {
		BlockEntity blockEntity = builder.getOptional(LootContextParameters.BLOCK_ENTITY);
		if (blockEntity instanceof PortableJukeboxBlockEntity jukebox) {
			return List.of(jukebox.getJukebox());
		}
		return super.getDroppedStacks(state, builder);
	}

	@Override
	protected void onStateReplaced(BlockState state, ServerWorld world, BlockPos pos, boolean moved) {
		BlockEntity blockEntity = world.getBlockEntity(pos);
		super.onStateReplaced(state, world, pos, moved);
		if (blockEntity instanceof PortableJukeboxBlockEntity jukebox) {
			world.updateComparators(pos, state.getBlock());
		}
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new PortableJukeboxBlockEntity(pos, state);
	}

	@Override
	protected boolean hasComparatorOutput(BlockState state) {
		return true;
	}

	@Override
	protected int getComparatorOutput(BlockState state, World world, BlockPos pos, Direction direction) {
		var blockEntity = world.getBlockEntity(pos);
		if (blockEntity instanceof PortableJukeboxBlockEntity portable) {
			return PortableJukeboxItem.getComparatorOutput(portable.getJukebox(), world.getRegistryManager());
		}
		return 0;
	}

	@Override
	protected void neighborUpdate(
			BlockState state, World world, BlockPos pos, Block sourceBlock,
			WireOrientation orientation, boolean notify
	) {
		var blockEntity = world.getBlockEntity(pos);
		if (blockEntity instanceof PortableJukeboxBlockEntity portable) {
			PortableJukeboxItem.updateRedstone(EntityRef.fromBlock(portable), portable.getJukebox());
		}
	}
}
