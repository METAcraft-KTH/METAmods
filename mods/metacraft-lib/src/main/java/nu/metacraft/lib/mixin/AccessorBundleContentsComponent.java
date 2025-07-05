package nu.metacraft.lib.mixin;

import net.minecraft.component.type.BundleContentsComponent;
import net.minecraft.item.ItemStack;
import org.apache.commons.lang3.math.Fraction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(BundleContentsComponent.class)
public interface AccessorBundleContentsComponent {

	@Invoker
	static Fraction callCalculateOccupancy(List<ItemStack> stacks) {
		throw new IllegalStateException("MixinError");
	}

}
