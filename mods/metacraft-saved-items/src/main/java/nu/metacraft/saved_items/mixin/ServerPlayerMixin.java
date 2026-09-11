package nu.metacraft.saved_items.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import nu.metacraft.saved_items.item_saving.ItemEntityData;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin extends LivingEntityMixin {

	@Shadow
	public abstract Level level();

	@Override
	public @Nullable ItemEntity createItemStackToDrop(
			ItemStack itemStack, boolean randomly, boolean thrownFromHand,
			Operation<ItemEntity> original
	) {
		var entity = original.call(itemStack, randomly, thrownFromHand);
		if (entity != null && !this.level().isClientSide() && this.isDeadOrDying()) {
			((ItemEntityData) entity).metacraft_saved_items$setDroppedByDeadPlayer((Player) (Object) this);
		}
		return entity;
	}

}
