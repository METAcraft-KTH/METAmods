package nu.metacraft.core.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.tag.EntityTypeTags;
import net.minecraft.world.World;
import nu.metacraft.core.status_effects.METAcraftEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity extends Entity {

	public MixinLivingEntity(EntityType<?> type, World world) {
		super(type, world);
	}

	@Inject(method = "canHaveStatusEffect", at = @At("HEAD"), cancellable = true)
	public void canHaveStatusEffect(
			StatusEffectInstance effect, CallbackInfoReturnable<Boolean> cir
	) {
		if (effect.getEffectType() == METAcraftEffects.FIRE && this.isFireImmune()) {
			cir.setReturnValue(false);
		}
		if (effect.getEffectType() == METAcraftEffects.FREEZE && this.getType().isIn(EntityTypeTags.FREEZE_IMMUNE_ENTITY_TYPES)) {
			cir.setReturnValue(false);
		}
	}

}
