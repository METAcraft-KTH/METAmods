package nu.metacraft.core.mixin;

import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.ConversionType;
import net.minecraft.world.entity.Mob;
import nu.metacraft.core.util.helper.BossBarHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ConversionType.class)
public class ConversionTypeMixin {

	@Inject(method = "convertCommon", at = @At("RETURN"))
	private static void copyData(Mob oldEntity, Mob newEntity, ConversionParams context, CallbackInfo ci) {
		BossBarHelper.getBossBar(oldEntity).ifPresent(bar -> {
			if (bar.isMainEntity(oldEntity)) {
				bar.setMainEntity(newEntity);
			}
			BossBarHelper.setBossBar(newEntity, bar);
			bar.addEntity(newEntity);
		});
	}

}
