package nu.metacraft.saved_items.mixin;

import com.mojang.datafixers.DSL;
import net.minecraft.util.datafix.DataFixTypes;
import nu.metacraft.saved_items.SavedItemsDataFixer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(DataFixTypes.class)
public enum DataFixTypesMixin {
	METACRAFT_SAVED_DATA_SAVED_ITEMS(SavedItemsDataFixer.SAVED_DATA_SAVED_ITEMS);

	@Shadow
	DataFixTypesMixin(final DSL.TypeReference type) {}

}
