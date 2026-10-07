package nu.metacraft.core.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.WritableLevelData;
import nu.metacraft.core.util.ServerSoundType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin extends Level {

	@Shadow
	public abstract ServerLevel getLevel();

	protected ServerLevelMixin(WritableLevelData levelData, ResourceKey<Level> dimension, RegistryAccess registryAccess, Holder<DimensionType> dimensionTypeRegistration, boolean isClientSide, boolean isDebug, long biomeZoomSeed, int maxChainedNeighborUpdates) {
		super(levelData, dimension, registryAccess, dimensionTypeRegistration, isClientSide, isDebug, biomeZoomSeed, maxChainedNeighborUpdates);
	}

	@Inject(
		method = "levelEvent",
		at = @At("HEAD")
	)
	public void fixEventSound(Entity source, int type, BlockPos pos, int data, CallbackInfo ci) {
		BlockState blockState = switch (type) {
			case LevelEvent.PARTICLES_AND_SOUND_DESTROY_BLOCK -> Block.stateById(data);
			case LevelEvent.PARTICLES_AND_SOUND_DESTROY_PROGRESS -> getLevel().getBlockState(pos);
			default -> null;
		};

		if (blockState != null && !blockState.isAir()) {
			if (blockState.getSoundType() instanceof ServerSoundType soundType) {
				switch (type) {
					case LevelEvent.PARTICLES_AND_SOUND_DESTROY_BLOCK -> playSound(
						null, pos, soundType.getBreakSound(), SoundSource.BLOCKS,
						(soundType.getVolume() + 1.0F) / 2.0F, soundType.getPitch() * 0.8F
					);
					case LevelEvent.PARTICLES_AND_SOUND_DESTROY_PROGRESS -> playSound(
						null, pos, soundType.getHitSound(), SoundSource.BLOCKS,
						(soundType.getVolume() + 1.0F) / 8.0F, soundType.getPitch() * 0.5F
					);
				}
			}
		}
	}

}
