package nu.metacraft.simplecustomfeatures.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import nu.metacraft.simplecustomfeatures.extension.ItemSettingsExtension;

@Mixin(Item.class)
public class ItemMixin {

	@ModifyArg(
		method = "<init>",
		at = @At(
				value = "INVOKE",
				target = "Lnet/minecraft/world/item/Item$Properties;buildAndValidateComponents(Lnet/minecraft/network/chat/Component;Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/core/component/DataComponentMap;"
		),
		index = 0
	)
	public Component init(Component name, @Local(argsOnly = true) Item.Properties settings) {
		var customName = ((ItemSettingsExtension) settings).simple_custom_features$getCustomName();
		if (customName != null) {
			return customName;
		}
		return name;
	}

	@Mixin(Item.Properties.class)
	public static class Properties implements ItemSettingsExtension {

		@Unique
		private Component customName = null;

		@Override
		public Component simple_custom_features$getCustomName() {
			return customName;
		}

		@Override
		public void simple_custom_features$setCustomName(Component name) {
			this.customName = name;
		}
	}

}
