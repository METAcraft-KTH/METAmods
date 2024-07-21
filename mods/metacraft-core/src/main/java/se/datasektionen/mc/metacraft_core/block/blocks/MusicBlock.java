package se.datasektionen.mc.metacraft_core.block.blocks;

import com.mojang.serialization.MapCodec;
import eu.pb4.polymer.core.api.block.PolymerBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import se.datasektionen.mc.metacraft_core.block.METAcraftBlockEntities;
import se.datasektionen.mc.metacraft_core.block.entities.MusicBlockEntity;

public class MusicBlock extends BlockWithEntity implements PolymerBlock {

	public static final EnumProperty<BlockRotation> ROTATION = EnumProperty.of("rotation", BlockRotation.class);
	public static final EnumProperty<BlockMirror> MIRROR = EnumProperty.of("mirror", BlockMirror.class);


	public static final MapCodec<MusicBlock> CODEC = createCodec(MusicBlock::new);
	public MusicBlock(Settings settings) {
		super(settings);
		this.setDefaultState(
			this.stateManager.getDefaultState().with(ROTATION, BlockRotation.NONE).with(MIRROR, BlockMirror.NONE)
		);
	}
	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(ROTATION);
		builder.add(MIRROR);
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state) {
		return Blocks.AIR.getDefaultState();
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state, ServerPlayerEntity player) {
		if (player.isCreative()) {
			return Blocks.TRIAL_SPAWNER.getDefaultState();
		} else {
			return getPolymerBlockState(state);
		}
	}

	@Override
	public BlockState rotate(BlockState state, BlockRotation rotation) {
		return state.with(ROTATION, state.get(ROTATION).rotate(rotation));
	}

	@Override
	public BlockState mirror(BlockState state, BlockMirror mirror) {
		switch (state.get(MIRROR)) {
			case NONE -> {
				state.with(MIRROR, mirror);
			}
			case FRONT_BACK -> {
				switch (mirror) {
					case FRONT_BACK -> {
						return state.with(MIRROR, BlockMirror.NONE);
					}
					case LEFT_RIGHT -> {
						return state.with(MIRROR, BlockMirror.NONE)
								.with(ROTATION, state.get(ROTATION).rotate(BlockRotation.CLOCKWISE_180));
					}
				}
			}
			case LEFT_RIGHT -> {
				switch (mirror) {
					case FRONT_BACK -> {
						return state.with(MIRROR, BlockMirror.NONE)
								.with(ROTATION, state.get(ROTATION).rotate(BlockRotation.CLOCKWISE_180));
					}
					case LEFT_RIGHT -> {
						return state.with(MIRROR, BlockMirror.NONE);
					}
				}
			}
		}
		return state;
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new MusicBlockEntity(pos, state);
	}

	@Override
	public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		if (!moved && !newState.isOf(this) && world.getBlockEntity(pos) instanceof MusicBlockEntity blockEntity) {
			blockEntity.resetMusic();
		}
		super.onStateReplaced(state, world, pos, newState, moved);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		return validateTicker(type, METAcraftBlockEntities.MUSIC_PLAYER, MusicBlockEntity::tick);
	}
}
