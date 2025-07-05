package nu.metacraft.cutscenes.mixin;

import net.minecraft.entity.effect.StatusEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(StatusEffectInstance.class)
public interface AccessorStatusEffectInstance {

	@Accessor
	StatusEffectInstance getHiddenEffect();

	@Invoker
	void callCopyFrom(StatusEffectInstance that);

}
