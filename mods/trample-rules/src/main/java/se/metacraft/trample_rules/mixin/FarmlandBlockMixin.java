package se.metacraft.trample_rules.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import se.metacraft.trample_rules.TrampleRules;

@Mixin(FarmlandBlock.class)
public class FarmlandBlockMixin {

	/**
	 * Only the trample in fallOn is gated. Farmland drying out (turnToBaseBlock called from tick)
	 * and everything else mobs can do are left alone. This can only prevent a trample that vanilla
	 * would allow, so mobGriefing=false still stops mobs.
	 */
	@WrapWithCondition(
			method = "fallOn",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/level/block/FarmlandBlock;turnToBaseBlock(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V"
			)
	)
	private boolean trampleRules$allowTrample(FarmlandBlock instance, Entity entity, BlockState state, Level level, BlockPos pos) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return true;
		}
		return serverLevel.getGameRules().get(
				entity instanceof Player ? TrampleRules.PLAYER_CROP_TRAMPLE : TrampleRules.MOB_TRAMPLE
		);
	}

}
