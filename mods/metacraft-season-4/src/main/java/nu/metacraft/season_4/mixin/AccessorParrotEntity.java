package nu.metacraft.season_4.mixin;

import net.minecraft.entity.passive.ParrotEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ParrotEntity.class)
public interface AccessorParrotEntity {

	@Invoker
	void callSetVariant(ParrotEntity.Variant variant);

}
