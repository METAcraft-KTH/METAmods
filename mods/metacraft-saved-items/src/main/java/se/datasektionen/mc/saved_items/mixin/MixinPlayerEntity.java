package se.datasektionen.mc.saved_items.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import se.datasektionen.mc.saved_items.item_saving.ItemEntityData;

@Mixin(ServerPlayerEntity.class)
public abstract class MixinPlayerEntity extends LivingEntity {

	protected MixinPlayerEntity(EntityType<? extends LivingEntity> entityType, World world) {
		super(entityType, world);
	}

	@ModifyReturnValue(
		method = "dropItem",
		at = @At("RETURN")
	)
	public ItemEntity dropItem(ItemEntity original, ItemStack stack, boolean throwRandomly, boolean retainOwnership) {
		if (original != null && !this.getWorld().isClient() && this.isDead()) {
			((ItemEntityData) original).metacraft_saved_items$setDroppedByDeadPlayer((PlayerEntity) (Object) this);
		}
		return original;
	}

}
