package se.metacraft.fake_players.mixin;

import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.OldUsersConverter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import se.metacraft.fake_players.Playback;

import java.util.UUID;

@Mixin(OldUsersConverter.class)
public class OldUsersConverterMixin {

	/**
	 * Carpet's {@code /player spawn} looks the bot name up at Mojang on the server thread, twice, and the names of
	 * our bots do not exist there. On an online-mode server that stalls the tick by about a second per bot.
	 */
	@Inject(method = "convertMobOwnerIfNecessary", at = @At("HEAD"), cancellable = true)
	private static void skipLookupForReplayBots(MinecraftServer server, String name, CallbackInfoReturnable<UUID> cir) {
		if (name != null && name.startsWith(Playback.NAME_PREFIX)) {
			cir.setReturnValue(UUIDUtil.createOfflinePlayerUUID(name));
		}
	}
}
