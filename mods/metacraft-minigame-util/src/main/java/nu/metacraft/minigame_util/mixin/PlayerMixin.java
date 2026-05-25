package nu.metacraft.minigame_util.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import nu.metacraft.minigame_util.MinigameUtilState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Player.class)
public abstract class PlayerMixin extends LivingEntity {

	protected PlayerMixin(EntityType<? extends LivingEntity> entityType, Level world) {
		super(entityType, world);
	}

	@ModifyReturnValue(
		method = "blockActionRestricted",
		at = @At("RETURN")
	)
	public boolean isBlockBreakingRestricted(boolean original, Level world, BlockPos pos) {
		if (world instanceof ServerLevel sw && !entityTags().contains("admin")) {
			return MinigameUtilState.getInstance(world.getServer()).canBreak(sw, pos).orElse(original);
		}
		return original;
	}

}
