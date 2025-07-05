package nu.metacraft.cutscenes.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.command.EntitySelector;
import net.minecraft.server.command.ServerCommandSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import nu.metacraft.cutscenes.cutscene.world.CutsceneWorld;

@Mixin(EntitySelector.class)
public class MixinEntitySelector {

	@ModifyExpressionValue(
		method = {
				"getEntities(Lnet/minecraft/server/command/ServerCommandSource;)Ljava/util/List;",
				"getPlayers"
		},
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/command/EntitySelector;isLocalWorldOnly()Z"
		)
	)
	public boolean getEntities(boolean original, @Local(argsOnly = true) ServerCommandSource source) {
		if (source.getWorld() instanceof CutsceneWorld) {
			return true;
		}
		return original;
	}

}
