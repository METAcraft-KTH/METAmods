package nu.metacraft.cutscenes.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;
import nu.metacraft.cutscenes.extension.EntityExtension;

@Mixin(ServerEntity.class)
public class ServerEntityMixin {

	@Shadow @Final private Entity entity;

	@ModifyExpressionValue(
			method = "sendChanges",
			at = @At(
					value = "FIELD",
					target = "Lnet/minecraft/server/level/ServerEntity;wasRiding:Z",
					ordinal = 0
			),
			slice = @Slice(
					from = @At(
							value = "INVOKE",
							target = "Lnet/minecraft/network/protocol/game/VecDeltaCodec;encodeX(Lnet/minecraft/world/phys/Vec3;)J"
					)
			)
	)
	public boolean shouldMoveAccurately(boolean original) {
		if (entity instanceof EntityExtension e && e.metacraft$hasAccurateMovement()) {
			return true;
		}
		return original;
	}

}
