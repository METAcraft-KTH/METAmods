package se.datasektionen.mc.portal_blocker.mixin;

import net.minecraft.block.AbstractFireBlock;
import net.minecraft.block.BlockState;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.datasektionen.mc.portal_blocker.PortalBlockerSettings;
import se.datasektionen.mc.portal_blocker.PortalState;
import se.datasektionen.mc.portal_blocker.portal_type.PortalTypeRegistry;

@Mixin(AbstractFireBlock.class)
public class MixinAbstractFireBlock {

	@Inject(
			method = "onBlockAdded",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/dimension/NetherPortal;createPortal(Lnet/minecraft/world/WorldAccess;)V"
			)
	)
	public void onBlockAdded(
			BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify, CallbackInfo ci
	) {
		if (world.getServer() != null) {
			if (PortalBlockerSettings.getInstance(world.getServer()).isPortalBlocked(
					PortalTypeRegistry.NETHER, world.getRegistryKey(), PortalState.BlockingType.CREATION, pos
			)) {
				world.removeBlock(pos, false);
				world.playSound(null, pos, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS);
				if (world instanceof ServerWorld sw) {
					Vec3d center = Vec3d.ofCenter(pos);
					sw.spawnParticles(
							ParticleTypes.LARGE_SMOKE, center.getX(), center.getY(), center.getZ(),
							10, 0.1, 0.1, 0.1, 0.1
					);
				}
			}
		}
	}

}
