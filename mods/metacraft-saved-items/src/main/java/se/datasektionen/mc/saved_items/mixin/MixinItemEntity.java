package se.datasektionen.mc.saved_items.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.datasektionen.mc.saved_items.item_saving.ItemEntityData;
import se.datasektionen.mc.saved_items.SavedItems;
import se.datasektionen.mc.saved_items.item_saving.SavedItemsData;

@Mixin(ItemEntity.class)
public abstract class MixinItemEntity extends Entity implements ItemEntityData {

	@Shadow public abstract ItemStack getStack();

	@Shadow public abstract void setStack(ItemStack stack);

	@Unique
	private static final String DROPPED_BY_DEAD_PLAYER = "DroppedByDeadPlayer";

	@Unique
	private Text playerName = null;

	public MixinItemEntity(EntityType<?> type, World world) {
		super(type, world);
	}

	@Override
	public Text metacraft_saved_items$getSourcePlayerName() {
		return playerName;
	}

	@Override
	public void metacraft_saved_items$setDroppedByDeadPlayer(PlayerEntity player) {
		this.playerName = player.getDisplayName();
	}

	@Inject(
		method = "writeCustomDataToNbt",
		at = @At("RETURN")
	)
	public void writeNBT(NbtCompound nbt, CallbackInfo ci) {
		if (playerName != null) {
			TextCodecs.CODEC.encodeStart(NbtOps.INSTANCE, playerName).resultOrPartial(
					SavedItems.LOGGER::error
			).ifPresent(name -> {
				nbt.put(DROPPED_BY_DEAD_PLAYER, name);
			});
		}
	}

	@Inject(
		method = "readCustomDataFromNbt",
		at = @At("RETURN")
	)
	public void readNBT(NbtCompound nbt, CallbackInfo ci) {
		if (nbt.contains(DROPPED_BY_DEAD_PLAYER)) {
			TextCodecs.CODEC.parse(NbtOps.INSTANCE, nbt.get(DROPPED_BY_DEAD_PLAYER)).resultOrPartial(
					SavedItems.LOGGER::error
			).ifPresent(name -> {
				playerName = name;
			});
		} else {
			playerName = null;
		}
	}

	@Inject(
		method = "tick",
		slice = @Slice(
			from = @At(
				value = "FIELD",
				target = "Lnet/minecraft/entity/ItemEntity;itemAge:I",
				ordinal = 0
			)
		),
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/entity/ItemEntity;discard()V"
		)
	)
	public void onDespawn(CallbackInfo ci) {
		if (playerName != null) {
			if (SavedItemsData.getInstance(this.getServer()).tryAddItem(SavedItemsData.DESPAWN_TYPE, this.getStack())) {
				this.setStack(ItemStack.EMPTY);
			}
		}
	}

	@WrapWithCondition(
		method = "damage",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/item/ItemStack;onItemEntityDestroyed(Lnet/minecraft/entity/ItemEntity;)V"
		)
	)
	public boolean onDestroy(ItemStack stack, ItemEntity entity, @Local(argsOnly = true) DamageSource source) {
		if (playerName != null) {
			if (SavedItemsData.getInstance(getServer()).tryAddItem(SavedItemsData.getForDamageType(source), stack)) {
				this.setStack(ItemStack.EMPTY);
				return false;
			}
		}
		return true;
	}
}
