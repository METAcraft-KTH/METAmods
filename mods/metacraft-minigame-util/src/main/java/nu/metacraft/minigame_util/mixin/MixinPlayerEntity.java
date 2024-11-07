package nu.metacraft.minigame_util.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import nu.metacraft.minigame_util.MinigameUtilState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PlayerEntity.class)
public abstract class MixinPlayerEntity extends LivingEntity {

	protected MixinPlayerEntity(EntityType<? extends LivingEntity> entityType, World world) {
		super(entityType, world);
	}

	@ModifyReturnValue(
		method = "isBlockBreakingRestricted",
		at = @At("RETURN")
	)
	public boolean isBlockBreakingRestricted(boolean original, World world, BlockPos pos) {
		if (world instanceof ServerWorld sw && !getCommandTags().contains("admin")) {
			return MinigameUtilState.getInstance(world.getServer()).canBreak(sw, pos).orElse(original);
		}
		return original;
	}

}
