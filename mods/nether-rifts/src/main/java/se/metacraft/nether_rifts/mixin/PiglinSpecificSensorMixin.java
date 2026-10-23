package se.metacraft.nether_rifts.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.sensing.PiglinSpecificSensor;
import se.metacraft.nether_rifts.raid.RiftMobs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Rift piglins (and the Ghast Bombardier, rift or not) don't honour the gold-armour truce: to them every player is a target. */
@Mixin(PiglinSpecificSensor.class)
public abstract class PiglinSpecificSensorMixin {

	@WrapOperation(
			method = "doTick",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/monster/piglin/PiglinAi;isWearingSafeArmor(Lnet/minecraft/world/entity/LivingEntity;)Z")
	)
	private boolean netherRifts$noTruce(LivingEntity player, Operation<Boolean> original, @Local(argsOnly = true) LivingEntity body) {
		return !RiftMobs.keepsRiftRules(body) && original.call(player);
	}
}
