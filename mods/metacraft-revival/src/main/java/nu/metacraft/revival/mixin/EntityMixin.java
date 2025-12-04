package nu.metacraft.revival.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import nu.metacraft.revival.extension.ServerPlayerExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Entity.class)
public class EntityMixin {

	@ModifyReturnValue(method = "interact", at = @At("RETURN"))
	public InteractionResult interact(InteractionResult original, @Local(argsOnly = true) Player otherPlayer) {
		//noinspection ConstantValue
		if ((Object) this instanceof ServerPlayer playerToRevive && original == InteractionResult.PASS) {
			var ext = (ServerPlayerExtension) playerToRevive;
			if (ext.metacraft$isUnconscious() && ext.metacraft$getReviver() == null) {
				ext.metacraft$setReviver(otherPlayer);
				return InteractionResult.SUCCESS_SERVER;
			}
		}
		return original;
	}

}
