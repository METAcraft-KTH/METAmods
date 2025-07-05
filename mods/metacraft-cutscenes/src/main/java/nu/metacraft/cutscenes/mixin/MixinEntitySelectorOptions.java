package nu.metacraft.cutscenes.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.command.EntitySelectorOptions;
import net.minecraft.entity.Entity;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import nu.metacraft.cutscenes.cutscene.world.CutsceneWorld;
import nu.metacraft.cutscenes.util.helper.CutsceneHelper;

@Mixin(EntitySelectorOptions.class)
public class MixinEntitySelectorOptions {

	@WrapOperation(
		method = "method_9937", //Lambda inside addPredicates inside "scores" inside register.
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/MinecraftServer;getScoreboard()Lnet/minecraft/scoreboard/ServerScoreboard;"
		)
	)
	private static ServerScoreboard getScoreboardFromWorld(
			MinecraftServer instance, Operation<ServerScoreboard> original,
			@Local(argsOnly = true) Entity entity
	) {
		if (entity.getWorld() instanceof CutsceneWorld w) {
			return w.getScoreboard();
		}
		if (entity instanceof ServerPlayerEntity p && CutsceneHelper.isInCutscene(p)) {
			return CutsceneHelper.getCutscene(p).orElseThrow().getCutsceneWorld().getScoreboard();
		}
		return original.call(instance);
	}

}
