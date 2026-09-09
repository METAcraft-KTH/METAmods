package nu.metacraft.moderation.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import nu.metacraft.moderation.EntitySelectorExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityArgument.class)
public class EntityArgumentMixin implements EntitySelectorExtension {

	@Unique
	private boolean metacraft$alwaysIncludeModerators = false;

	@Override
	public void metacraft$setAlwaysIncludeModerators(boolean alwaysIncludeModerators) {
		metacraft$alwaysIncludeModerators = alwaysIncludeModerators;
	}

	@ModifyReturnValue(
			method = "parse(Lcom/mojang/brigadier/StringReader;Z)Lnet/minecraft/commands/arguments/selector/EntitySelector;",
			at = @At("RETURN")
	)
	private EntitySelector parse(EntitySelector original) {
		((EntitySelectorExtension) original).metacraft$setAlwaysIncludeModerators(metacraft$alwaysIncludeModerators);
		return original;
	}
}
