package nu.metacraft.portable_jukebox.block;

import com.mojang.serialization.MapCodec;
import eu.pb4.polymer.core.api.block.PolymerHeadBlock;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import nu.metacraft.lib.util.EntityRef;
import nu.metacraft.portable_jukebox.item.PortableJukeboxItem;
import nu.metacraft.portable_jukebox.entity.PortableJukeboxEntity;
import nu.metacraft.portable_jukebox.gui.PortableJukeboxGui;
import nu.metacraft.portable_jukebox.mixin.SkullBlockAccessor;

import java.util.List;

public class PortableJukeboxBlock extends BaseEntityBlock implements PolymerHeadBlock {

	public static final MapCodec<PortableJukeboxBlock> CODEC = EnderChestBlock.simpleCodec(PortableJukeboxBlock::new);

	public PortableJukeboxBlock(Properties settings) {
		super(settings);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
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
