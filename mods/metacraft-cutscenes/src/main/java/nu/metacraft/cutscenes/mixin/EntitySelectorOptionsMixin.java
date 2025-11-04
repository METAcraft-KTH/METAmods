package nu.metacraft.cutscenes.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.commands.arguments.selector.options.EntitySelectorOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import nu.metacraft.cutscenes.cutscene.world.CutsceneWorld;
import nu.metacraft.cutscenes.util.helper.CutsceneHelper;

@Mixin(EntitySelectorOptions.class)
public class EntitySelectorOptionsMixin {

	@WrapOperation(
		method = "method_9937", //Lambda inside addPredicates inside "scores" inside register.
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/MinecraftServer;getScoreboard()Lnet/minecraft/server/ServerScoreboard;"
		)
	)
	private static ServerScoreboard getScoreboardFromWorld(
			MinecraftServer instance, Operation<ServerScoreboard> original,
			@Local(argsOnly = true) Entity entity
	) {
		if (entity.level() instanceof CutsceneWorld w) {
			return w.getScoreboard();
		}
		if (entity instanceof ServerPlayer p && CutsceneHelper.isInCutscene(p)) {
			return CutsceneHelper.getCutscene(p).orElseThrow().getCutsceneWorld().getScoreboard();
		}
		return original.call(instance);
	}

}
