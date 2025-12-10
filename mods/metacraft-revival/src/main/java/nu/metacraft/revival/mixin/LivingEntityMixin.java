package nu.metacraft.revival.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.LivingEntity;
import nu.metacraft.revival.extension.ServerPlayerExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {

	@ModifyReturnValue(method = "canBeSeenByAnyone", at = @At("RETURN"))
	public boolean canBeSeenByAnyone(boolean original) {
		if (this instanceof ServerPlayerExtension ext && ext.metacraft$isUnconscious()) {
			return false;
		}
		return original;
	}

}
