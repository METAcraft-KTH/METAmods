package nu.metacraft.simplecustomfeatures.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import nu.metacraft.simplecustomfeatures.ComponentInitializerPass;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import nu.metacraft.simplecustomfeatures.extension.ItemPropertiesExtension;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Item.class)
public class ItemMixin {

	@Inject(
		method = "<init>",
		at = @At(
				value = "INVOKE",
				target = "Lnet/minecraft/core/component/DataComponentInitializers;add(Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/core/component/DataComponentInitializers$Initializer;)V"
		)
	)
	public void onAdd(Item.Properties properties, CallbackInfo ci) {
		ComponentInitializerPass.pass(this);
	}

	@Mixin(Item.Properties.class)
	public static class Properties implements ItemPropertiesExtension {

		@Unique
		private boolean isCustom = false;

		@Unique
		private static final ThreadLocal<Component> NAME = ThreadLocal.withInitial(() -> null);

		@Unique
		private static final ThreadLocal<Identifier> MODEL = ThreadLocal.withInitial(() -> null);

		@ModifyExpressionValue(
				method = "finalizeInitializer",
				at = @At(
						value = "FIELD",
						target = "Lnet/minecraft/world/item/Item$Properties;componentInitializer:Lnet/minecraft/core/component/DataComponentInitializers$Initializer;",
						opcode = Opcodes.GETFIELD
				)
		)
		public DataComponentInitializers.Initializer<Item> before(DataComponentInitializers.Initializer<Item> original) {
			if (isCustom) {
				return original.andThen((components, context, key) -> {
					if (components.contains(DataComponents.ITEM_MODEL)) {
						MODEL.set(components.getOrCreate(DataComponents.ITEM_MODEL, () -> null));
					}
					if (components.contains(DataComponents.ITEM_NAME)) {
						NAME.set(components.getOrCreate(DataComponents.ITEM_NAME, () -> null));
					}
				});
			}
			return original;
		}

		@ModifyReturnValue(
				method = "finalizeInitializer",
				at = @At("RETURN")
		)
		public <T> DataComponentInitializers.Initializer<Item> init(
				DataComponentInitializers.Initializer<Item> original
		) {
			if (isCustom) {
				return original.andThen((components, context, key) -> {
					var model = MODEL.get();
					MODEL.remove();
					var name = NAME.get();
					NAME.remove();
					if (model != null) {
						components.set(DataComponents.ITEM_MODEL, model);
					}
					if (name != null) {
						components.set(DataComponents.ITEM_NAME, name);
					}
				});
			}
			return original;
		}

		@Override
		public void simple_custom_features$setIsCustom(boolean isCustom) {
			this.isCustom = isCustom;
		}
	}

}
