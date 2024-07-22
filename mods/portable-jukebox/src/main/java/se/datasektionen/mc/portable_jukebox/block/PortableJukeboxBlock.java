package se.datasektionen.mc.portable_jukebox.block;

import com.mojang.serialization.MapCodec;
import eu.pb4.polymer.core.api.block.PolymerHeadBlock;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import se.datasektionen.mc.metacraft_lib.util.EntityRef;
import se.datasektionen.mc.portable_jukebox.item.PortableJukeboxItem;
import se.datasektionen.mc.portable_jukebox.entity.PortableJukeboxEntity;
import se.datasektionen.mc.portable_jukebox.gui.PortableJukeboxGui;

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
	public String getPolymerSkinValue(BlockState state, BlockPos pos, ServerPlayerEntity player) {
		//From: https://minecraft-heads.com/player-heads/head/3645-jukebox
		return "ewogICJ0aW1lc3RhbXAiIDogMTcxODc5MzE5MzU4NiwKICAicHJvZmlsZUlkIiA6ICIxZjA1NGRlNDgwZmI0NjA0OWE0N2NlMmNiYWE0MjJkMiIsCiAgInByb2ZpbGVOYW1lIiA6ICJQaW5nUG9uZ0RlbGF5IiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2JhZGU4ZjJiYjBhZDVjZDlmNWY5MGRjY2JjYTZlYmU3ZmEzZjk4YTU1OTgyMmNkMGIwNDQ1YzVjNzcyZGJiYTciCiAgICB9CiAgfQp9";
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
	protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		BlockEntity blockEntity = world.getBlockEntity(pos);
		if (blockEntity instanceof PortableJukeboxBlockEntity jukebox) {
			ItemScatterer.spawn(world, pos.getX(), pos.getY(), pos.getZ(), jukebox.getJukebox());
		}
		super.onStateReplaced(state, world, pos, newState, moved);
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
	protected int getComparatorOutput(BlockState state, World world, BlockPos pos) {
		var blockEntity = world.getBlockEntity(pos);
		if (blockEntity instanceof PortableJukeboxBlockEntity portable) {
			return PortableJukeboxItem.getComparatorOutput(portable.getJukebox(), world.getRegistryManager());
		}
		return 0;
	}

	@Override
	protected void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
		var blockEntity = world.getBlockEntity(pos);
		if (blockEntity instanceof PortableJukeboxBlockEntity portable) {
			PortableJukeboxItem.updateRedstone(EntityRef.fromBlock(portable), portable.getJukebox());
		}
	}
}
