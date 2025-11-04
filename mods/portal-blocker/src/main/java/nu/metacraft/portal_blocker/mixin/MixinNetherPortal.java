package nu.metacraft.portal_blocker.mixin;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nu.metacraft.portal_blocker.PortalBlocker;
import nu.metacraft.portal_blocker.PortalBlockerSettings;
import nu.metacraft.portal_blocker.PortalState;
import nu.metacraft.portal_blocker.portal_type.PortalTypeRegistry;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraft.world.phys.AABB;

@Mixin(value = PortalShape.class, priority = 999)
public class MixinNetherPortal {

	@Final
	@Shadow private @Nullable BlockPos bottomLeft;

	@Final
	@Shadow private int height;

	@Shadow @Final private Direction rightDir;

	@Shadow @Final private int width;

	@Inject(method = "createPortalBlocks", at = @At("HEAD"), cancellable = true)
	public void create(LevelAccessor world, CallbackInfo ci) {
		if (world.getServer() != null && world instanceof Level w) {
			Iterable<BlockPos> positions = List.of();
			BlockPos.MutableBlockPos center = new BlockPos.MutableBlockPos();
			boolean foundConfigurablePortals = false;
			try {
				var blocksField = this.getClass().getDeclaredField("blocks");
				var blocks = blocksField.get(this);
				if (blocks instanceof List<?> blockList && !blockList.isEmpty()) {
					var pos = blockList.getFirst();
					if (pos instanceof BlockPos) {
						positions = (List<BlockPos>) blockList;
						center.set(BoundingBox.encapsulatingPositions(positions).orElseThrow().getCenter());
						foundConfigurablePortals = true;
					}
				}
			} catch (NoSuchFieldException | IllegalAccessException ignored) {}
			if (!foundConfigurablePortals) {
				if (this.bottomLeft == null) {
					PortalBlocker.LOGGER.error("Unable to block portal creation because some mod changes portal creation!");
					return;
				}
				positions = BlockPos.betweenClosed(this.bottomLeft, this.bottomLeft.relative(Direction.UP, this.height - 1).relative(this.rightDir, this.width - 1));
				center.set(this.bottomLeft.relative(Direction.UP, this.height/2).relative(this.rightDir, this.width/2));
			}
			if (PortalBlockerSettings.getInstance(world.getServer()).isPortalBlocked(
					PortalTypeRegistry.NETHER, w.dimension(), PortalState.BlockingType.ACTIVATION, positions
			)) {
				PortalTypeRegistry.NETHER.getCreationMessage().ifPresent(msg -> {
					world.getEntitiesOfClass(Player.class, AABB.encapsulatingFullBlocks(
							center.south(6).east(6).below(6),
							center.north(6).west(6).above(6)
					), player -> true).forEach(player -> {
						player.displayClientMessage(msg, true);
					});
				});
				ci.cancel();
			}
			if (PortalBlockerSettings.getInstance(world.getServer()).blockPortalCreationOutsideBorder()
					&& outsideWorldBorderOnOtherSide(world, center)) {
				Component msg = PortalTypeRegistry.NETHER.getOutsideBorderMessage();
				world.getEntitiesOfClass(Player.class, AABB.encapsulatingFullBlocks(
						center.south(6).east(6).below(6),
						center.north(6).west(6).above(6)
				), player -> true).forEach(player -> {
					player.displayClientMessage(msg, true);
				});
				ci.cancel();
			}
		} else {
			PortalBlocker.LOGGER.warn(
					"Portal created in non-server world. Some mod you have installed might allow players to bypass portal-blocker!"
			);
		}
	}

	@Unique
	private boolean outsideWorldBorderOnOtherSide(LevelAccessor worldAccess, BlockPos.MutableBlockPos center) {
	    if (!(worldAccess instanceof Level world) || world.getServer() == null) {
	        return false;
	    }

	    ResourceKey<Level> currentDim = world.dimension();
	    ResourceKey<Level> targetDim;

	    if (currentDim == Level.OVERWORLD) {
	        targetDim = Level.NETHER;
	    } else if (currentDim == Level.NETHER) {
	        targetDim = Level.OVERWORLD;
	    } else {
	        return false;
	    }

	    ServerLevel targetWorld = world.getServer().getLevel(targetDim);
	    if (targetWorld == null) {
	        return false;
	    }
		double coordinateScale = DimensionType.getTeleportationScale(world.dimensionType(), targetWorld.dimensionType());
	    WorldBorder targetBorder = targetWorld.getWorldBorder();

	    double targetX = center.getX() * coordinateScale;
	    double targetZ = center.getZ() * coordinateScale;

	    return !targetBorder.isWithinBounds(targetX, targetZ);
	}

}
