package nu.metacraft.cutscenes.mixin;

import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MobEffectInstance.class)
public interface AccessorStatusEffectInstance {

	@Accessor
	MobEffectInstance getHiddenEffect();

	@Invoker
	void callSetDetailsFrom(MobEffectInstance that);

}
