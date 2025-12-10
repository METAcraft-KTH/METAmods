package nu.metacraft.season_5.mixin;

import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AreaEffectCloud.class)
public interface AreaEffectCloudAccessor {

	@Accessor
	EntityReference<LivingEntity> getOwner();

}
