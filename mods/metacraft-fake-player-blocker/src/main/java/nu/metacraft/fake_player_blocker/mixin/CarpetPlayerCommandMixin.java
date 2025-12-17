package nu.metacraft.fake_player_blocker.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import nu.metacraft.fake_player_blocker.FakePlayerBlocker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "carpet.commands.PlayerCommand")
public class CarpetPlayerCommandMixin {

	@Inject(
			method = "cantSpawn",
			at = @At(
					value = "INVOKE",
					target = "isSpawningPlayer"
			),
			remap = false,
			cancellable = true
	)
	private static void cantSpawn(
			CommandContext<CommandSourceStack> ctx, CallbackInfoReturnable<Boolean> cir,
			@Local String playerName
	) {
		if (!FakePlayerBlocker.canSpawn(ctx, playerName)) {
			cir.setReturnValue(true);
		}
	}

}
