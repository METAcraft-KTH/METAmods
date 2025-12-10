package nu.metacraft.core.mixin;

import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import net.minecraft.core.component.DataComponentType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PolymerItemUtils.class)
public interface PolymerItemUtilsAccessor {

	@Accessor("COMPONENTS_TO_COPY")
	static DataComponentType<?>[] getComponentsToCopy() {
		throw new IllegalStateException("Mixin Error");
	}

	@Mutable
	@Accessor("COMPONENTS_TO_COPY")
	static void setComponentsToCopy(DataComponentType<?>[] componentsToCopy) {
		throw new IllegalStateException("Mixin Error");
	}

}
