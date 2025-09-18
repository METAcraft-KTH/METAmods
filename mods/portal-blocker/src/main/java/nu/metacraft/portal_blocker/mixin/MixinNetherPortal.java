package nu.metacraft.portal_blocker.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockBox;
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
import nu.metacraft.portal_blocker.PortalBlocker;
import nu.metacraft.portal_blocker.PortalBlockerSettings;
import nu.metacraft.portal_blocker.PortalState;
import nu.metacraft.portal_blocker.portal_type.PortalTypeRegistry;

import java.util.List;

@Mixin(value = NetherPortal.class, priority = 999)
public class MixinNetherPortal {

	@Final
	@Shadow private @Nullable BlockPos lowerCorner;

	@Final
	@Shadow private int height;

	@Shadow @Final private Direction negativeDir;

	@Shadow @Final private int width;

	@Inject(method = "createPortal", at = @At("HEAD"), cancellable = true)
	public void create(WorldAccess world, CallbackInfo ci) {
		if (world.getServer() != null && world instanceof World w) {
			Iterable<BlockPos> positions = List.of();
			BlockPos.Mutable center = new BlockPos.Mutable();
			boolean foundConfigurablePortals = false;
			try {
				var blocksField = this.getClass().getDeclaredField("blocks");
				var blocks = blocksField.get(this);
				if (blocks instanceof List<?> blockList && !blockList.isEmpty()) {
					var pos = blockList.getFirst();
					if (pos instanceof BlockPos) {
						positions = (List<BlockPos>) blockList;
						center.set(BlockBox.encompassPositions(positions).orElseThrow().getCenter());
						foundConfigurablePortals = true;
					}
				}
			} catch (NoSuchFieldException | IllegalAccessException ignored) {}
			if (!foundConfigurablePortals) {
				if (this.lowerCorner == null) {
					PortalBlocker.LOGGER.error("Unable to block portal creation because some mod changes portal creation!");
					return;
				}
				positions = BlockPos.iterate(this.lowerCorner, this.lowerCorner.offset(Direction.UP, this.height - 1).offset(this.negativeDir, this.width - 1));
				center.set(this.lowerCorner.offset(Direction.UP, this.height/2).offset(this.negativeDir, this.width/2));
			}
			if (PortalBlockerSettings.getInstance(world.getServer()).isPortalBlocked(
					PortalTypeRegistry.NETHER, w.getRegistryKey(), PortalState.BlockingType.ACTIVATION, positions
			)) {
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
