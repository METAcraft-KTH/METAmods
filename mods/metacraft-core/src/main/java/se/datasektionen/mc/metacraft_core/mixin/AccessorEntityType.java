package se.datasektionen.mc.metacraft_core.mixin;

import net.minecraft.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityType.class)
public interface AccessorEntityType {

	@Accessor
	@Mutable
	void setTranslationKey(String translationKey);

}
