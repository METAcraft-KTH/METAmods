package nu.metacraft.mob_modifiers.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.LevelReader;
import nu.metacraft.mob_modifiers.extensions.MonsterExtensions;

@Mixin(Monster.class)
public class MonsterMixin implements MonsterExtensions {

	@Unique
	private boolean survivesSunlight = false;

	@Inject(
		method = "getWalkTargetValue", at = @At("HEAD"),
		cancellable = true
	)
	public void noFearDarkness(BlockPos pos, LevelReader world, CallbackInfoReturnable<Float> cir) {
		if (survivesSunlight) {
			cir.setReturnValue(0.0f);
		}
	}

	@Override
	public boolean metacraft_lib$survivesSunlight() {
		return survivesSunlight;
	}

	@Override
	public void metacraft_lib$setSurvivesSunlight(boolean survivesSunlight) {
		this.survivesSunlight = survivesSunlight;
	}
}
