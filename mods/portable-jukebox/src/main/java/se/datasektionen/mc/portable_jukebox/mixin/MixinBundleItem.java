package se.datasektionen.mc.portable_jukebox.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.component.type.BundleContentsComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BundleItem;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import se.datasektionen.mc.metacraft_lib.util.EntityRef;
import se.datasektionen.mc.portable_jukebox.entity.PortableJukeboxEntity;

@Mixin(BundleItem.class)
public class MixinBundleItem {

	@ModifyExpressionValue(
		method = "onClicked",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/component/type/BundleContentsComponent$Builder;add(Lnet/minecraft/item/ItemStack;)I"
		)
	)
	public int onClicked(
			int original, @Local(argsOnly = true) Slot slot, @Local(argsOnly = true) PlayerEntity player, @Local BundleContentsComponent.Builder builder
	) {
		var bundleStacks = ((AccessorBundleContentsComponentBuilder) builder).getStacks();
		if (!bundleStacks.isEmpty()) {
			PortableJukeboxEntity.transferToInventory(
					EntityRef.fromEntity(player), bundleStacks.getFirst(), slot.inventory
			);
		}
		return original;
	}

}
