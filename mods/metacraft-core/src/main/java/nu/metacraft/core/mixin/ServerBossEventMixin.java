package nu.metacraft.core.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.server.level.ServerBossEvent;
import nu.metacraft.core.extensions.CommandBossBarExtension;

@Mixin(ServerBossEvent.class)
public class ServerBossEventMixin {

	@Inject(method = "setVisible", at = @At("RETURN"))
	public void setVisible(boolean visible, CallbackInfo ci) {
		if ((Object) this instanceof CommandBossBarExtension b) {
			b.metacraft_core$getMusicHandler().onToggleVisibility(visible);
		}
	}

}
