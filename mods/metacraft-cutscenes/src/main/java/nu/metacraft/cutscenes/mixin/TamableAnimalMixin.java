package nu.metacraft.cutscenes.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import nu.metacraft.cutscenes.cutscene.world.CutsceneWorld;
import nu.metacraft.cutscenes.util.helper.CutsceneHelper;

@Mixin(TamableAnimal.class)
public abstract class TamableAnimalMixin extends Animal {

	protected TamableAnimalMixin(EntityType<? extends Animal> entityType, Level world) {
		super(entityType, world);
	}

	@WrapOperation(
		method = "unableToMoveToOwner",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/LivingEntity;isSpectator()Z"
			)
	)
	public boolean cannotFollowOwner(LivingEntity owner, Operation<Boolean> original) {
		if (owner instanceof ServerPlayer player) {
			var scene = CutsceneHelper.getCutscene(player);
			if (scene.isPresent()) {
				if (this.level() != scene.get().getCutsceneWorld()) return true;
			} else {
				if (this.level() instanceof CutsceneWorld) return true;
			}
		}
		return original.call(owner);
	}

}
