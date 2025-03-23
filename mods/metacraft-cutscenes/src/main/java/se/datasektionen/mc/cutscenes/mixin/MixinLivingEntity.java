package se.datasektionen.mc.cutscenes.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import se.datasektionen.mc.cutscenes.cutscene.CutsceneInstance;
import se.datasektionen.mc.cutscenes.util.helper.CutsceneHelper;

@Mixin(LivingEntity.class)
public class MixinLivingEntity {

	@WrapOperation(
			method = "dropItem",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/World;spawnEntity(Lnet/minecraft/entity/Entity;)Z"
			)
	)
	public boolean dropItem(
			World world, Entity entity, Operation<Boolean> original
	) {
		if ((Object) this instanceof ServerPlayerEntity player) {
			var scene = CutsceneHelper.getCutscene(player);
			if (scene.isPresent() && scene.get().getCutscene().resetPlayerData()) {
				scene.get().addEntity(CutsceneInstance.PLAYER_ITEM, entity);
				return true;
			}
		}
		return original.call(world, entity);
	}

}
