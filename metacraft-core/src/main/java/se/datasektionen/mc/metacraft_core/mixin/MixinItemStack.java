package se.datasektionen.mc.metacraft_core.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.serialization.Codec;
import net.minecraft.component.ComponentMapImpl;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.datasektionen.mc.metacraft_core.item.ItemModifiers;

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

	@ModifyReturnValue(method = "fromNbt", at = @At("RETURN"))
	private static Optional<ItemStack> fromNbt(
			Optional<ItemStack> original
	) {
		return original.map(ItemModifiers::setToEmptyOnLoad);
	}

	@Inject(method = "inventoryTick", at = @At("RETURN"))
	public void inventoryTick(World world, Entity entity, int slot, boolean selected, CallbackInfo ci) {
		if (entity instanceof ServerPlayerEntity player) {
			ItemModifiers.modifyTick(
					(ItemStack) (Object) this, player.getRandom()
			).ifPresent(result -> {
				if (player.getInventory().main.get(slot) == (Object) this) {
					player.getInventory().main.set(slot, result);
				} else if (player.getInventory().armor.get(slot) == (Object) this) {
					player.getInventory().armor.set(slot, result);
				} else if (player.getInventory().offHand.get(slot) == (Object) this) {
					player.getInventory().offHand.set(slot, result);
				}
			});
		}
	}
}
