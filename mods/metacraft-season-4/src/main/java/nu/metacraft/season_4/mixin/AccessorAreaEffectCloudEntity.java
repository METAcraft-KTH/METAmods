package nu.metacraft.season_4.mixin;

import net.minecraft.entity.AreaEffectCloudEntity;
import net.minecraft.entity.LazyEntityReference;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AreaEffectCloudEntity.class)
public interface AccessorAreaEffectCloudEntity {

	@Accessor
	LazyEntityReference<LivingEntity> getOwner();

}
