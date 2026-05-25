package nu.metacraft.simplecustomfeatures.mixin;

import net.minecraft.core.component.DataComponentInitializers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(DataComponentInitializers.class)
public interface DataComponentInitializersAccessor {

	@Accessor
	List<DataComponentInitializers.InitializerEntry<?>> getInitializers();

	@Mixin(DataComponentInitializers.InitializerEntry.class)
	interface InitializerEntry {
		@Accessor
		@Mutable
		<T> void setInitializer(DataComponentInitializers.Initializer<T> initializer);
	}

}
