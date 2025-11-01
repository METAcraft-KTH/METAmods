package nu.metacraft.core.mixin;

import net.minecraft.entity.conversion.EntityConversionContext;
import net.minecraft.entity.conversion.EntityConversionType;
import net.minecraft.entity.mob.MobEntity;
import nu.metacraft.core.util.helper.BossBarHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityConversionType.class)
public class MixinEntityConversionType {

	@Inject(method = "copyData", at = @At("RETURN"))
	public void copyData(MobEntity oldEntity, MobEntity newEntity, EntityConversionContext context, CallbackInfo ci) {
		BossBarHelper.getBossBar(oldEntity).ifPresent(bar -> {
			if (bar.isMainEntity(oldEntity)) {
				bar.setMainEntity(newEntity);
			}
			BossBarHelper.setBossBar(newEntity, bar);
			bar.addEntity(newEntity);
		});
	}

}
