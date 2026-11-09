package se.metacraft.skins.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.metacraft.skins.MetacraftSkins;
import se.metacraft.skins.MobHats;
import se.metacraft.skins.Skins;

/**
 * As an entity dies, before its loot: a hat-wearing mob drops its Skin Box ({@link MobHats#died}).
 * Here rather than in the loot itself, which a baby never drops.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@Inject(method = "dropAllDeathLoot", at = @At("HEAD"))
	private void dropHatBox(ServerLevel level, DamageSource source, CallbackInfo ci) {
		Skins skins = MetacraftSkins.skins();
		if (skins != null) MobHats.died(skins, (LivingEntity) (Object) this, level);
	}
}
