package nu.metacraft.cutscenes.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.selector.EntitySelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import nu.metacraft.cutscenes.cutscene.world.CutsceneLevel;

@Mixin(EntitySelector.class)
public class EntitySelectorMixin {

	@ModifyExpressionValue(
		method = {
				"findEntities(Lnet/minecraft/commands/CommandSourceStack;)Ljava/util/List;",
				"findPlayers"
		},
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/commands/arguments/selector/EntitySelector;isWorldLimited()Z"
		)
	)
	public boolean getEntities(boolean original, @Local(argsOnly = true) CommandSourceStack source) {
		if (source.getLevel() instanceof CutsceneLevel) {
			return true;
		}
		return original;
	}

}
