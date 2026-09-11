package nu.metacraft.portable_jukebox.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.component.BundleContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import nu.metacraft.lib.util.EntityRef;
import nu.metacraft.portable_jukebox.entity.PortableJukeboxEntity;

@Mixin(BundleItem.class)
public class BundleItemMixin {

	@ModifyExpressionValue(
		method = "overrideOtherStackedOnMe",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/item/component/BundleContents$Mutable;tryInsert(Lnet/minecraft/world/item/ItemStack;)I"
		)
	)
	public int onClicked(
			int original, @Local(argsOnly = true) Slot slot, @Local(argsOnly = true) Player player, @Local BundleContents.Mutable builder
	) {
		var bundleStacks = ((SimpleMutableContainerAccessor) builder).getItems();
		if (!bundleStacks.isEmpty()) {
			PortableJukeboxEntity.transferToInventory(
					EntityRef.fromEntity(player), bundleStacks.getFirst(), slot.container
			);
		}
		return original;
	}

}
