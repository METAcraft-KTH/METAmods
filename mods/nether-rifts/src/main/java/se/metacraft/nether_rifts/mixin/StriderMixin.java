package se.metacraft.nether_rifts.mixin;

import net.minecraft.world.entity.monster.Strider;
import se.metacraft.nether_rifts.raid.RiftMobs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * A rift's strider brings the nether's heat with it: off lava in the overworld it never goes cold, so it neither shivers
 * nor slows (a cold strider walks at two thirds of its speed, a third of its piglin rider's, who steers it).
 */
@Mixin(Strider.class)
public abstract class StriderMixin {

	@ModifyArg(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/monster/Strider;setSuffocating(Z)V"))
	private boolean netherRifts$riftStridersStayWarm(boolean suffocating) {
		return suffocating && !RiftMobs.isRiftMob((Strider) (Object) this);
	}
}
