package nu.metacraft.simplecustomfeatures.mixin;

import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKeyedValue;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Item.Settings.class)
public interface AccessorItemSettings {

	@Accessor
	void setModelId(RegistryKeyedValue<Item, Identifier> id);

}
