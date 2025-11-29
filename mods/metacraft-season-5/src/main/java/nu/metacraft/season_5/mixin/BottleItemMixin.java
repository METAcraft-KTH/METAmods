package nu.metacraft.season_5.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.item.BottleItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;

@Mixin(BottleItem.class)
public class BottleItemMixin {

	@WrapOperation(
			method = "method_7726", //First lambda inside use
			constant = @Constant(classValue = EnderDragon.class)
	)
	private static boolean makeMcmakisteinDragonBreathPossibleToPickUp(Object object, Operation<Boolean> original, @Local(argsOnly = true) AreaEffectCloud cloud) {
		var ownerEntity = object instanceof Entity e ? e : null;
		if (ownerEntity == null && cloud instanceof AreaEffectCloudAccessor e) {
			var owner = e.getOwner();
			if (cloud.level() instanceof ServerLevel world && owner != null) {
				ownerEntity = world.getEntity(owner.getUUID());
			}
		}
		if (ownerEntity != null && ownerEntity.getTags().contains("aj.impossible_dragon.root")) {
			return true;
		}
		return original.call(object);
	}

}
