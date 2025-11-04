package nu.metacraft.bundles.mixin;

import org.apache.commons.lang3.math.Fraction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;

@Mixin(BundleContents.class)
public interface BundleContentsAccessor {

	@Invoker
	static Fraction callComputeContentWeight(List<ItemStack> stacks) {
		throw new IllegalStateException("MixinError");
	}

}
