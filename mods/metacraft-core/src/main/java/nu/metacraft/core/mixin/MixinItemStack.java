package nu.metacraft.core.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.serialization.Codec;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import nu.metacraft.core.item.ItemModifiers;

import java.util.Optional;

@Mixin(ItemStack.class)
public class MixinItemStack {

	@ModifyExpressionValue(
		method = "<clinit>",
		at = @At(
			value = "INVOKE",
			target = "Lcom/mojang/serialization/Codec;lazyInitialized(Ljava/util/function/Supplier;)Lcom/mojang/serialization/Codec;"
		)
	)
	private static Codec<ItemStack> fixCodec1(Codec<ItemStack> original) {
		return ItemModifiers.wrap(original.xmap(ItemModifiers::modifyLoad, s -> s));
	}

	@ModifyExpressionValue(
			method = "<clinit>",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/util/dynamic/Codecs;optional(Lcom/mojang/serialization/Codec;)Lcom/mojang/serialization/Codec;"
			)
	)
	private static Codec<Optional<ItemStack>> fixCodec2(Codec<Optional<ItemStack>> original) {
		return original.xmap(ItemModifiers::deleteOnLoad, s -> s);
	}
}
