package nu.metacraft.cutscenes.mixin;

import net.minecraft.network.packet.c2s.common.ResourcePackStatusC2SPacket;
import net.minecraft.server.network.ServerCommonNetworkHandler;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nu.metacraft.cutscenes.transitions.PlaySoundTransition;
import nu.metacraft.cutscenes.util.helper.CutsceneHelper;

@Mixin(ServerCommonNetworkHandler.class)
public class MixinServerCommonNetworkHandler {

	@Unique
	private int lastResourcePackTime = -1;

	@Inject(method = "onResourcePackStatus", at = @At("RETURN"))
	public void onResourcePackStatus(ResourcePackStatusC2SPacket packet, CallbackInfo ci) {
		if ((Object) this instanceof ServerPlayNetworkHandler h) {
			if (packet.status() == ResourcePackStatusC2SPacket.Status.SUCCESSFULLY_LOADED) {
				int time = h.getPlayer().age;
				if (time <= lastResourcePackTime) {
					return;
				}
				lastResourcePackTime = time;
				CutsceneHelper.getCutscene(h.getPlayer()).ifPresent(cutscene -> {
					cutscene.getTransitions().getIntervalsAt(cutscene.getCurrentTime()).filter(
							t -> t.getObject() instanceof PlaySoundTransition &&
									t.getEnd() != cutscene.getCurrentTime()
					).forEach(t -> {
						t.getObject().activate(h.getPlayer(), cutscene, t);
					});
				});
			}
		}
	}

}
