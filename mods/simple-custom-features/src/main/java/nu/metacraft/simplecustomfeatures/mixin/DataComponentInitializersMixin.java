package nu.metacraft.simplecustomfeatures.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.core.component.DataComponentInitializers;
import nu.metacraft.simplecustomfeatures.ComponentInitializerPass;
import nu.metacraft.simplecustomfeatures.extension.DataComponentInitializersExtension;
import nu.metacraft.simplecustomfeatures.extension.InitializerEntryExtension;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(DataComponentInitializers.class)
public class DataComponentInitializersMixin implements DataComponentInitializersExtension {

	@Shadow
	@Final
	private List<DataComponentInitializers.InitializerEntry<?>> initializers;

	@ModifyExpressionValue(
			method = "add",
			at = @At(
					value = "NEW",
					target = "(Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/core/component/DataComponentInitializers$Initializer;)Lnet/minecraft/core/component/DataComponentInitializers$InitializerEntry;"
			)
	)
	public <T> DataComponentInitializers.InitializerEntry<T> add(DataComponentInitializers.InitializerEntry<T> original) {
		((InitializerEntryExtension) (Object) original).simple_custom_features$setObject(ComponentInitializerPass.catchIt());
		return original;
	}

	@Override
	public void simple_custom_features$removeInitializer(Object object) {
		initializers.removeIf(
				init -> ((InitializerEntryExtension) (Object) init).simple_custom_features$getObject() == object
		);
	}

	@Mixin(DataComponentInitializers.InitializerEntry.class)
	public static class InitializerEntry implements InitializerEntryExtension {

		@Unique
		private Object object;

		@Override
		public void simple_custom_features$setObject(Object object) {
			this.object = object;
		}

		@Override
		public Object simple_custom_features$getObject() {
			return object;
		}
	}
}
