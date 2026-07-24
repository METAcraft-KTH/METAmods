package nu.metacraft.moderation.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.commands.BossBarCommands;
import net.minecraft.server.commands.PlaySoundCommand;
import net.minecraft.server.commands.StopSoundCommand;
import nu.metacraft.lib.commands.PlaySoundFromEntity;
import nu.metacraft.moderation.EntitySelectorExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({PlaySoundFromEntity.class, BossBarCommands.class, StopSoundCommand.class})
public class AlwaysTargetModeratorCommandMixins {

	@ModifyExpressionValue(
			method = "register",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/commands/arguments/EntityArgument;players()Lnet/minecraft/commands/arguments/EntityArgument;"
			)
	)
	private static EntityArgument includeModerators(EntityArgument original) {
		((EntitySelectorExtension) original).metacraft$setAlwaysIncludeModerators(true);
		return original;
	}

}
