package se.datasektionen.mc.metacraft_core.mixin;

import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import net.minecraft.component.ComponentType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PolymerItemUtils.class)
public interface AccessorPolymerItemUtils {

	@Accessor("COMPONENTS_TO_COPY")
	static ComponentType<?>[] getComponentsToCopy() {
		throw new IllegalStateException("Mixin Error");
	}

	@Mutable
	@Accessor("COMPONENTS_TO_COPY")
	static void setComponentsToCopy(ComponentType<?>[] componentsToCopy) {
		throw new IllegalStateException("Mixin Error");
	}

}
