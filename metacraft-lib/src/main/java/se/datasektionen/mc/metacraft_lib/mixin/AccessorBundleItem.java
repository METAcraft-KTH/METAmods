package se.datasektionen.mc.metacraft_lib.mixin;

import net.minecraft.item.BundleItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BundleItem.class)
public interface AccessorBundleItem {

	@Accessor("field_51352")
	static int getBundleMaxSize() {
		throw new IllegalStateException("Mixin failure");
	}

}
