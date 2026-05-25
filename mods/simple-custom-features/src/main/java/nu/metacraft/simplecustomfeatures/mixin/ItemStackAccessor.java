package nu.metacraft.simplecustomfeatures.mixin;

import com.mojang.serialization.DataResult;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.throwables.MixinException;

@Mixin(ItemStack.class)
public interface ItemStackAccessor {

	@Invoker
	static DataResult<?> callValidateComponents(final DataComponentMap components) {
		throw new MixinException("Failed to apply");
	}

}
