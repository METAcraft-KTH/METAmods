package nu.metacraft.core.block.blocks;

import eu.pb4.polymer.core.api.block.PolymerBlock;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jetbrains.annotations.Nullable;
import nu.metacraft.core.block.METAcraftBlockEntities;
import nu.metacraft.core.block.entities.MusicBlockEntity;

public class MusicBlock extends BaseEntityBlock implements PolymerBlock {

	public static final EnumProperty<Rotation> ROTATION = EnumProperty.create("rotation", Rotation.class);
	public static final EnumProperty<Mirror> MIRROR = EnumProperty.create("mirror", Mirror.class);


	public MusicBlock(Properties settings) {
		super(settings);
		this.registerDefaultState(
			this.stateDefinition.any().setValue(ROTATION, Rotation.NONE).setValue(MIRROR, Mirror.NONE)
		);
	}
	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(ROTATION);
		builder.add(MIRROR);
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext ctx) {
		if (ctx != null) {
			var profile = ctx.get(PacketContext.GAME_PROFILE);
			var server = ctx.get(PacketContext.SERVER_INSTANCE);
			if (profile != null && server != null) {
				var player = server.getPlayerList().getPlayer(profile.id());
				if (player != null && player.isCreative()) {
					return Blocks.TRIAL_SPAWNER.defaultBlockState();
				}
			}
		}
		return Blocks.AIR.defaultBlockState();
	}

	@Override
	public BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(ROTATION, state.getValue(ROTATION).getRotated(rotation));
	}

	@Override
	public BlockState mirror(BlockState state, Mirror mirror) {
		switch (state.getValue(MIRROR)) {
			case NONE -> {
				state.setValue(MIRROR, mirror);
			}
			case FRONT_BACK -> {
				switch (mirror) {
					case FRONT_BACK -> {
						return state.setValue(MIRROR, Mirror.NONE);
					}
					case LEFT_RIGHT -> {
						return state.setValue(MIRROR, Mirror.NONE)
								.setValue(ROTATION, state.getValue(ROTATION).getRotated(Rotation.CLOCKWISE_180));
					}
				}
			}
			case LEFT_RIGHT -> {
				switch (mirror) {
					case FRONT_BACK -> {
						return state.setValue(MIRROR, Mirror.NONE)
								.setValue(ROTATION, state.getValue(ROTATION).getRotated(Rotation.CLOCKWISE_180));
					}
					case LEFT_RIGHT -> {
						return state.setValue(MIRROR, Mirror.NONE);
					}
				}
			}
		}
		return state;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new MusicBlockEntity(pos, state);
	}

	@Override
	public void affectNeighborsAfterRemoval(BlockState state, ServerLevel world, BlockPos pos, boolean moved) {
		if (!moved && !world.getBlockState(pos).is(this) && world.getBlockEntity(pos) instanceof MusicBlockEntity blockEntity) {
			blockEntity.resetMusic();
		}
		super.affectNeighborsAfterRemoval(state, world, pos, moved);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type) {
		return createTickerHelper(type, METAcraftBlockEntities.MUSIC_PLAYER, MusicBlockEntity::tick);
	}
}
