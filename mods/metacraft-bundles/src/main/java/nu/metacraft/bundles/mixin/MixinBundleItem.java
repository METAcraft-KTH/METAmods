package nu.metacraft.bundles.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.component.type.BundleContentsComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.StackReference;
import net.minecraft.item.BundleItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.PlaySoundFromEntityS2CPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ClickType;
import nu.metacraft.bundles.util.BundleHelper;
import nu.metacraft.core.mixin.AccessorScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import nu.metacraft.lib.util.TaskScheduler;

@Mixin(BundleItem.class)
public abstract class MixinBundleItem {

	@Inject(
			method = "onClicked",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/item/BundleItem;playInsertSound(Lnet/minecraft/entity/Entity;)V"
			)
	)
	public void onAdd(
			ItemStack bundle, ItemStack otherStack, Slot slot, ClickType clickType, PlayerEntity player,
			StackReference cursorStackReference, CallbackInfoReturnable<Boolean> cir, @Local BundleContentsComponent.Builder builder
	) {
		playSoundIfNecessary(player, builder);
	}

	@Inject(
			method = "onStackClicked",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/item/BundleItem;playInsertSound(Lnet/minecraft/entity/Entity;)V"
			)
	)
	public void onAdd(
			ItemStack bundle, Slot slot, ClickType clickType, PlayerEntity player, CallbackInfoReturnable<Boolean> cir,
			@Local BundleContentsComponent.Builder builder
	) {
		playSoundIfNecessary(player, builder);
	}

	@Unique
	private void playSoundIfNecessary(PlayerEntity player, BundleContentsComponent.Builder builder) {
		var factor = BundleHelper.getStoredBundleSizeFactor(builder);
		if (builder.getOccupancy().multiplyBy(factor).doubleValue() >= 1 && player instanceof ServerPlayerEntity p) {
			p.networkHandler.sendPacket(new PlaySoundFromEntityS2CPacket(
					RegistryEntry.of(SoundEvents.ITEM_BUNDLE_INSERT), player.getSoundCategory(), player, 0.8f,
					0.8f + player.getWorld().getRandom().nextFloat() * 0.4f, player.getWorld().getRandom().nextLong()
			));
		}
	}

	@Inject(
			method = {
					"onClicked",
					"onStackClicked"
			},
			at = @At("RETURN")
	)
	public void updateIfClicked(
			CallbackInfoReturnable<Boolean> cir, @Local(argsOnly = true) Slot slot,
			@Local(argsOnly = true) PlayerEntity player
	) {
		if (!player.getWorld().isClient()) {
			var handler = player.currentScreenHandler;
			TaskScheduler.scheduleImmediately(
				player.getServer(), () -> {
					if (player.currentScreenHandler == handler) {
						((AccessorScreenHandler) player.currentScreenHandler).getSyncHandler().updateSlot(
								player.currentScreenHandler, slot.id, slot.getStack()
						);
						((AccessorScreenHandler) player.currentScreenHandler).getSyncHandler().updateCursorStack(
								player.currentScreenHandler, player.currentScreenHandler.getCursorStack()
						);
					}
				}
			);
		}
	}

}
