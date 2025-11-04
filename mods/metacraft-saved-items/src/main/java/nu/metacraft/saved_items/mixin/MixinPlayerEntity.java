package nu.metacraft.saved_items.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import nu.metacraft.saved_items.item_saving.ItemEntityData;

@Mixin(ServerPlayer.class)
public abstract class MixinPlayerEntity extends LivingEntity {

	protected MixinPlayerEntity(EntityType<? extends LivingEntity> entityType, Level world) {
		super(entityType, world);
	}

	@ModifyReturnValue(
		method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;",
		at = @At("RETURN")
	)
	public ItemEntity dropItem(ItemEntity original, ItemStack stack, boolean throwRandomly, boolean retainOwnership) {
		if (original != null && !this.level().isClientSide() && this.isDeadOrDying()) {
			((ItemEntityData) original).metacraft_saved_items$setDroppedByDeadPlayer((Player) (Object) this);
		}
		return original;
	}

}
