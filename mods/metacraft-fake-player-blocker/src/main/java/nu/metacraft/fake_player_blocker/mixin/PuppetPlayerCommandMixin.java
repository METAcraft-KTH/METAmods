package nu.metacraft.fake_player_blocker.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.fake_player_blocker.FakePlayerBlocker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "me.senseiwells.puppet.command.PuppetPlayerCommand")
public class PuppetPlayerCommandMixin {

	@Inject(
			method = "spawnFakePlayer",
			at = @At(
					value = "INVOKE",
					target = "addFakePlayerOrThrow"
			),
			remap = false,
			cancellable = true
	)
	private void spawnFakePlayer(
			CommandContext<CommandSourceStack> ctx, Vec3 pos, Vec2 rot, ServerLevel dim, GameType gameType,
			CallbackInfoReturnable<Integer> cir, @Local String username
	) {
		if (!FakePlayerBlocker.canSpawn(ctx, username)) {
			cir.setReturnValue(0);
		}
	}

}
