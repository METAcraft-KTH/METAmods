package se.datasektionen.mc.portable_jukebox.mixin;

import net.minecraft.component.type.BundleContentsComponent;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(BundleContentsComponent.Builder.class)
public interface AccessorBundleContentsComponentBuilder {

	@Accessor
	List<ItemStack> getStacks();

}
