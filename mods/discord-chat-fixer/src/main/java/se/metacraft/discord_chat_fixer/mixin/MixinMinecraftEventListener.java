package se.metacraft.discord_chat_fixer.mixin;

import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.datasektionen.mc.metacraft_lib.util.helper.PlayerDataHelper;

@Pseudo
@Mixin(targets = "com.xujiayao.discord_mc_chat.minecraft.MinecraftEventListener")
public class MixinMinecraftEventListener {

	@Inject(method = "lambda$init$4", at = @At("HEAD"), cancellable = true)
	private static void checkAdvancement(
			ServerPlayerEntity player, AdvancementEntry entry,
			boolean isDone, CallbackInfo ci
	) {
		if (!PlayerDataHelper.getAnnounceAdvancements(player)) {
			ci.cancel();
		}
	}

	@Inject(method = "lambda$init$5", at = @At("HEAD"), cancellable = true)
	private static void checkDeathMessage(
			ServerPlayerEntity player, CallbackInfo ci
	) {
		if (!PlayerDataHelper.getAnnounceDeath(player)) {
			ci.cancel();
		}
	}

	@Inject(method = "lambda$init$6", at = @At("HEAD"), cancellable = true)
	private static void checkJoin(
			ServerPlayerEntity player, CallbackInfo ci
	) {
		if (!PlayerDataHelper.getAnnounceJoinLeave(player)) {
			ci.cancel();
		}
	}


	@Inject(method = "lambda$init$7", at = @At("HEAD"), cancellable = true)
	private static void checkLeave(
			ServerPlayerEntity player, CallbackInfo ci
	) {
		if (!PlayerDataHelper.getAnnounceJoinLeave(player)) {
			ci.cancel();
		}
	}
}
