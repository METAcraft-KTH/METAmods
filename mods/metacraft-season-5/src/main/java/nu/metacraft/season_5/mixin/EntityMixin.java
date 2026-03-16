package nu.metacraft.season_5.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.season_5.METAcraftSeason5;
import nu.metacraft.season_5.S5GameRules;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMixin {

	@Shadow
	private Level level;

	@Shadow
	private Vec3 position;

	@Shadow
	public abstract @Nullable Entity teleport(TeleportTransition teleportTransition);

	@Shadow
	private float yRot;

	@Shadow
	private float xRot;

	@Shadow
	public abstract Vec3 getDeltaMovement();

	@Shadow
	public abstract AABB getBoundingBox();

	@Shadow
	public abstract BlockPos adjustSpawnLocation(ServerLevel serverLevel, BlockPos blockPos);

	@Unique
	private Vec3 getPosInRespawnDim(ServerLevel respawnDim) {
		var target = this.adjustSpawnLocation(respawnDim, respawnDim.getRespawnData().pos()).getBottomCenter();
		double y = respawnDim.getMaxY() + 32;
		var entityBox = getBoundingBox().move(position.reverse());
		var border = respawnDim.getWorldBorder();
		return new Vec3(
				Mth.clamp(target.x, border.getMinX() - entityBox.minX, border.getMaxX() - entityBox.maxX),
				y,
				Mth.clamp(target.z, border.getMinZ() - entityBox.minZ, border.getMaxZ() - entityBox.maxZ)
		);
	}

	@Inject(method = "tick", at = @At("RETURN"))
	public void tick(CallbackInfo ci) {
		if (level instanceof ServerLevel sl && sl.getGameRules().get(S5GameRules.END_VOID_OVERWORLD_TELEPORT)) {
			if (level.dimension() == Level.END && position.y < level.getMinY() - 32) {
				var respawnDim = sl.getServer().findRespawnDimension();
				//noinspection ConstantValue
				if (respawnDim == null || !METAcraftSeason5.shouldVoidTeleport((Entity) (Object) this)) return;
				teleport(new TeleportTransition(
						respawnDim, getPosInRespawnDim(respawnDim),
						getDeltaMovement(), yRot, xRot, TeleportTransition.DO_NOTHING
				));
			}
		}
	}

}
