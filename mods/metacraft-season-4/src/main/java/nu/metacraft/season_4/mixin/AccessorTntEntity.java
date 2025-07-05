package nu.metacraft.season_4.mixin;

import net.minecraft.entity.TntEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TntEntity.class)
public interface AccessorTntEntity {

	@Accessor
	void setExplosionPower(float explosionPower);

}
