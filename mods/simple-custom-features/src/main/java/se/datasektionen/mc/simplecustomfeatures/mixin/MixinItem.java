package se.datasektionen.mc.simplecustomfeatures.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.item.Item;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import se.datasektionen.mc.simplecustomfeatures.extension.ItemSettingsExtension;

@Mixin(Item.class)
public class MixinItem {

	@ModifyArg(
		method = "<init>",
		at = @At(
				value = "INVOKE",
				target = "Lnet/minecraft/item/Item$Settings;getValidatedComponents(Lnet/minecraft/text/Text;Lnet/minecraft/util/Identifier;)Lnet/minecraft/component/ComponentMap;"
		),
		index = 0
	)
	public Text init(Text name, @Local(argsOnly = true) Item.Settings settings) {
		var customName = ((ItemSettingsExtension) settings).simple_custom_features$getCustomName();
		if (customName != null) {
			return customName;
		}
		return name;
	}

	@Mixin(Item.Settings.class)
	public static class Settings implements ItemSettingsExtension {

		@Unique
		private Text customName = null;

		@Override
		public Text simple_custom_features$getCustomName() {
			return customName;
		}

		@Override
		public void simple_custom_features$setCustomName(Text name) {
			this.customName = name;
		}
	}

}
