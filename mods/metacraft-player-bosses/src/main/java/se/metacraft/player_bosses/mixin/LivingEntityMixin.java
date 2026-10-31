package se.metacraft.player_bosses.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import se.metacraft.player_bosses.state.PlayerBossState;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {

	@ModifyReturnValue(
		method = "calculateFallDamage",
		at = @At("RETURN")
	)
	public int computeFallDamage(int original) {
		if ((Object) this instanceof ServerPlayer player) {
			return PlayerBossState.getInstance(player.level()).filter(
				state -> state.isBoss(player)
			).map(s -> 0).orElse(original);
		}
		return original;
	}

}
