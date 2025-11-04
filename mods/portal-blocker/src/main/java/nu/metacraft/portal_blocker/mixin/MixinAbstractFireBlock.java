package nu.metacraft.portal_blocker.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.portal_blocker.PortalBlockerSettings;
import nu.metacraft.portal_blocker.PortalState;
import nu.metacraft.portal_blocker.portal_type.PortalTypeRegistry;

@Mixin(BaseFireBlock.class)
public class MixinAbstractFireBlock {

	@Inject(
			method = "onPlace",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/level/portal/PortalShape;createPortalBlocks(Lnet/minecraft/world/level/LevelAccessor;)V"
			)
	)
	public void onBlockAdded(
			BlockState state, Level world, BlockPos pos, BlockState oldState, boolean notify, CallbackInfo ci
	) {
		if (world.getServer() != null) {
			if (PortalBlockerSettings.getInstance(world.getServer()).isPortalBlocked(
					PortalTypeRegistry.NETHER, world.dimension(), PortalState.BlockingType.ACTIVATION, pos
			)) {
				world.removeBlock(pos, false);
				world.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS);
				if (world instanceof ServerLevel sw) {
					Vec3 center = Vec3.atCenterOf(pos);
					sw.sendParticles(
							ParticleTypes.LARGE_SMOKE, center.x(), center.y(), center.z(),
							10, 0.1, 0.1, 0.1, 0.1
					);
				}
			}
		}
	}

}
