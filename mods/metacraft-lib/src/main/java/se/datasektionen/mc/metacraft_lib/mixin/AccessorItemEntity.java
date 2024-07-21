package se.datasektionen.mc.metacraft_lib.mixin;

import net.minecraft.entity.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemEntity.class)
public interface AccessorItemEntity {

	@Accessor
	int getHealth();

}
