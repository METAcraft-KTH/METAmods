package nu.metacraft.simplecustomfeatures.mixin;

import net.minecraft.resources.DependantName;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Item.Properties.class)
public interface ItemPropertiesAccessor {

	@Accessor
	void setModel(DependantName<Item, Identifier> id);

}
