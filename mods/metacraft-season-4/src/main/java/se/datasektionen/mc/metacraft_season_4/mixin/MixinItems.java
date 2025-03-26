package se.datasektionen.mc.metacraft_season_4.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EnchantableComponent;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(Items.class)
public class MixinItems {

	@ModifyExpressionValue(
		method = "<clinit>",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/item/Item$Settings;component(Lnet/minecraft/component/ComponentType;Ljava/lang/Object;)Lnet/minecraft/item/Item$Settings;"
		),
		slice = @Slice(
				from = @At(
						value = "FIELD",
						target = "Lnet/minecraft/component/DataComponentTypes;DEATH_PROTECTION:Lnet/minecraft/component/ComponentType;"
				),
				to = @At(
						value = "FIELD",
						target = "Lnet/minecraft/item/Items;TOTEM_OF_UNDYING:Lnet/minecraft/item/Item;"
				)
		)
	)
	private static Item.Settings fixTotem(Item.Settings original) {
		return original.component(DataComponentTypes.ENCHANTABLE, new EnchantableComponent(1));
	}

}
