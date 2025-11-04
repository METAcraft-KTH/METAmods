package nu.metacraft.cutscenes.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import nu.metacraft.cutscenes.transitions.PlaySoundTransition;
import nu.metacraft.cutscenes.util.helper.CutsceneHelper;

@Mixin(ServerCommonPacketListenerImpl.class)
public class MixinServerCommonNetworkHandler {

	@Unique
	private int lastResourcePackTime = -1;

	@Inject(method = "handleResourcePackResponse", at = @At("RETURN"))
	public void onResourcePackStatus(ServerboundResourcePackPacket packet, CallbackInfo ci) {
		if ((Object) this instanceof ServerGamePacketListenerImpl h) {
			if (packet.action() == ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED) {
				int time = h.getPlayer().tickCount;
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
