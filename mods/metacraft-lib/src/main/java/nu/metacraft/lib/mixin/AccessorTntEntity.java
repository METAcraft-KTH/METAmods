package nu.metacraft.lib.mixin;

import net.minecraft.entity.LazyEntityReference;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.TntEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TntEntity.class)
public interface AccessorTntEntity {

	@Accessor
	void setCausingEntity(LazyEntityReference<LivingEntity> living);

}
