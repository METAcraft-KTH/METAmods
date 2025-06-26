package se.datasektionen.mc.metacraft_season_4.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.entity.AreaEffectCloudEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.item.GlassBottleItem;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;

@Mixin(GlassBottleItem.class)
public class MixinGlassBottleItem {

	@WrapOperation(
			method = "method_7726", //First lambda inside use
			constant = @Constant(classValue = EnderDragonEntity.class)
	)
	private static boolean makeMcmakisteinDragonBreathPossibleToPickUp(Object object, Operation<Boolean> original, @Local(argsOnly = true) AreaEffectCloudEntity cloud) {
		var ownerEntity = object instanceof Entity e ? e : null;
		if (ownerEntity == null && cloud instanceof AccessorAreaEffectCloudEntity e) {
			var owner = e.getOwner();
			if (cloud.getWorld() instanceof ServerWorld world && owner != null) {
				ownerEntity = world.getEntity(owner.getUuid());
			}
		}
		if (ownerEntity != null && ownerEntity.getCommandTags().contains("aj.impossible_dragon.root")) {
			return true;
		}
		return original.call(object);
	}

}
