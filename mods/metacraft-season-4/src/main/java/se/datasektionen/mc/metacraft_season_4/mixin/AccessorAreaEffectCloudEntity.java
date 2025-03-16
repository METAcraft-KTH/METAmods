package se.datasektionen.mc.metacraft_season_4.mixin;

import net.minecraft.entity.AreaEffectCloudEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.UUID;

@Mixin(AreaEffectCloudEntity.class)
public interface AccessorAreaEffectCloudEntity {

	@Accessor
	UUID getOwnerUuid();

}
