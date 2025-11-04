package nu.metacraft.cutscenes.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import nu.metacraft.cutscenes.cutscene.CutsceneInstance;
import nu.metacraft.cutscenes.util.helper.CutsceneHelper;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {

	@WrapOperation(
			method = "drop",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"
			)
	)
	public boolean dropItem(
			Level world, Entity entity, Operation<Boolean> original
	) {
		if ((Object) this instanceof ServerPlayer player) {
			var scene = CutsceneHelper.getCutscene(player);
			if (scene.isPresent() && scene.get().getCutscene().resetPlayerData()) {
				scene.get().addEntity(CutsceneInstance.PLAYER_ITEM, entity);
				return true;
			}
		}
		return original.call(world, entity);
	}

}
