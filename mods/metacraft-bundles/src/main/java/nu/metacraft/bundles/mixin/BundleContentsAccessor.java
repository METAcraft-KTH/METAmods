package nu.metacraft.bundles.mixin;

import com.mojang.serialization.DataResult;
import org.apache.commons.lang3.math.Fraction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;
import java.util.function.Supplier;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;

@Mixin(BundleContents.class)
public interface BundleContentsAccessor {

	@Invoker
	static DataResult<Fraction> callComputeContentWeight(List<ItemStack> stacks) {
		throw new IllegalStateException("MixinError");
	}

	@Accessor
	@Mutable
	void setWeight(Supplier<DataResult<Fraction>> weight);

}
