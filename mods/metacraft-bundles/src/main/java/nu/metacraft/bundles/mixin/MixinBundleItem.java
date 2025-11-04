package nu.metacraft.bundles.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
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
			method = "overrideOtherStackedOnMe",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/item/BundleItem;playInsertSound(Lnet/minecraft/world/entity/Entity;)V"
			)
	)
	public void onAdd(
			ItemStack bundle, ItemStack otherStack, Slot slot, ClickAction clickType, Player player,
			SlotAccess cursorStackReference, CallbackInfoReturnable<Boolean> cir, @Local BundleContents.Mutable builder
	) {
		playSoundIfNecessary(player, builder);
	}

	@Inject(
			method = "overrideStackedOnOther",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/item/BundleItem;playInsertSound(Lnet/minecraft/world/entity/Entity;)V"
			)
	)
	public void onAdd(
			ItemStack bundle, Slot slot, ClickAction clickType, Player player, CallbackInfoReturnable<Boolean> cir,
			@Local BundleContents.Mutable builder
	) {
		playSoundIfNecessary(player, builder);
	}

	@Unique
	private void playSoundIfNecessary(Player player, BundleContents.Mutable builder) {
		var factor = BundleHelper.getStoredBundleSizeFactor(builder);
		if (builder.weight().multiplyBy(factor).doubleValue() >= 1 && player instanceof ServerPlayer p) {
			p.connection.send(new ClientboundSoundEntityPacket(
					Holder.direct(SoundEvents.BUNDLE_INSERT), player.getSoundSource(), player, 0.8f,
					0.8f + player.level().getRandom().nextFloat() * 0.4f, player.level().getRandom().nextLong()
			));
		}
	}

	@Inject(
			method = {
					"overrideOtherStackedOnMe",
					"overrideStackedOnOther"
			},
			at = @At("RETURN")
	)
	public void updateIfClicked(
			CallbackInfoReturnable<Boolean> cir, @Local(argsOnly = true) Slot slot,
			@Local(argsOnly = true) Player player
	) {
		if (!player.level().isClientSide()) {
			var handler = player.containerMenu;
			TaskScheduler.scheduleImmediately(
				player.level().getServer(), () -> {
					if (player.containerMenu == handler) {
						((AccessorScreenHandler) player.containerMenu).getSynchronizer().sendSlotChange(
								player.containerMenu, slot.index, slot.getItem()
						);
						((AccessorScreenHandler) player.containerMenu).getSynchronizer().sendCarriedChange(
								player.containerMenu, player.containerMenu.getCarried()
						);
					}
				}
			);
		}
	}

}
