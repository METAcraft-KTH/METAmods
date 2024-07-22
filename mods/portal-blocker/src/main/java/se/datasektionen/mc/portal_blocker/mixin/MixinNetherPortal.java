package se.datasektionen.mc.portal_blocker.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.dimension.NetherPortal;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.datasektionen.mc.portal_blocker.PortalBlocker;
import se.datasektionen.mc.portal_blocker.PortalBlockerSettings;
import se.datasektionen.mc.portal_blocker.PortalState;
import se.datasektionen.mc.portal_blocker.portal_type.PortalTypeRegistry;

@Mixin(NetherPortal.class)
public class MixinNetherPortal {

	@Shadow @Final private WorldAccess world;

	@Shadow private @Nullable BlockPos lowerCorner;

	@Shadow private int height;

	@Shadow @Final private Direction negativeDir;

	@Shadow @Final private int width;

	@Inject(method = "createPortal", at = @At("HEAD"), cancellable = true)
	public void create(CallbackInfo ci) {
		if (world.getServer() != null && world instanceof World w) {
			if (PortalBlockerSettings.getInstance(world.getServer()).isPortalBlocked(
					PortalTypeRegistry.NETHER, w.getRegistryKey(), PortalState.BlockingType.CREATION,
					BlockPos.iterate(this.lowerCorner, this.lowerCorner.offset(Direction.UP, this.height - 1).offset(this.negativeDir, this.width - 1))
			)) {
				BlockPos center = this.lowerCorner.offset(Direction.UP, this.height/2).offset(this.negativeDir, this.width/2);
				PortalTypeRegistry.NETHER.getCreationMessage().ifPresent(msg -> {
					world.getEntitiesByClass(PlayerEntity.class, Box.enclosing(
							center.south(6).east(6).down(6),
							center.north(6).west(6).up(6)
					), player -> true).forEach(player -> {
						player.sendMessage(msg, true);
					});
				});
				ci.cancel();
			}
		} else {
			PortalBlocker.LOGGER.warn(
					"Portal created in non-server world. Some mod you have installed might allow players to bypass portal-blocker!"
			);
		}
	}

}
