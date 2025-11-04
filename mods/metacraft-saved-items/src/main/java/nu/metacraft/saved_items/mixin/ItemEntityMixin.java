package nu.metacraft.saved_items.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nu.metacraft.saved_items.item_saving.ItemEntityData;
import nu.metacraft.saved_items.item_saving.SavedItemsData;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin extends Entity implements ItemEntityData {

	@Shadow public abstract ItemStack getItem();

	@Shadow public abstract void setItem(ItemStack stack);

	@Unique
	private static final String DROPPED_BY_DEAD_PLAYER = "DroppedByDeadPlayer";

	@Unique
	private Component playerName = null;

	public ItemEntityMixin(EntityType<?> type, Level world) {
		super(type, world);
	}

	@Override
	public Component metacraft_saved_items$getSourcePlayerName() {
		return playerName;
	}

	@Override
	public void metacraft_saved_items$setDroppedByDeadPlayer(Player player) {
		this.playerName = player.getDisplayName();
	}

	@Inject(
		method = "addAdditionalSaveData",
		at = @At("RETURN")
	)
	public void writeNBT(ValueOutput nbt, CallbackInfo ci) {
		nbt.storeNullable(DROPPED_BY_DEAD_PLAYER, ComponentSerialization.CODEC, playerName);
	}

	@Inject(
		method = "readAdditionalSaveData",
		at = @At("RETURN")
	)
	public void readNBT(ValueInput nbt, CallbackInfo ci) {
		playerName = nbt.read(DROPPED_BY_DEAD_PLAYER, ComponentSerialization.CODEC).orElse(null);
	}

	@Inject(
		method = "tick",
		slice = @Slice(
			from = @At(
				value = "FIELD",
				target = "Lnet/minecraft/world/entity/item/ItemEntity;age:I",
				ordinal = 0
			)
		),
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/item/ItemEntity;discard()V"
		)
	)
	public void onDespawn(CallbackInfo ci) {
		if (playerName != null) {
			if (SavedItemsData.getInstance(this.level().getServer()).tryAddItem(SavedItemsData.DESPAWN_TYPE, this.getItem())) {
				this.setItem(ItemStack.EMPTY);
			}
		}
	}

	@WrapWithCondition(
		method = "hurtServer",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/item/ItemStack;onDestroyed(Lnet/minecraft/world/entity/item/ItemEntity;)V"
		)
	)
	public boolean onDestroy(ItemStack stack, ItemEntity entity, @Local(argsOnly = true) DamageSource source) {
		if (playerName != null) {
			if (SavedItemsData.getInstance(level().getServer()).tryAddItem(SavedItemsData.getForDamageType(source), stack)) {
				this.setItem(ItemStack.EMPTY);
				return false;
			}
		}
		return true;
	}
}
