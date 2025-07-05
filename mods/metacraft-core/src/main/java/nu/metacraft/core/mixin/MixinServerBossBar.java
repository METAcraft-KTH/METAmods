package nu.metacraft.core.mixin;

import net.minecraft.entity.boss.ServerBossBar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nu.metacraft.core.extensions.CommandBossBarExtension;

@Mixin(ServerBossBar.class)
public class MixinServerBossBar {

	@Inject(method = "setVisible", at = @At("RETURN"))
	public void setVisible(boolean visible, CallbackInfo ci) {
		if ((Object) this instanceof CommandBossBarExtension b) {
			b.metacraft_core$getMusicHandler().onToggleVisibility(visible);
		}
	}

}
