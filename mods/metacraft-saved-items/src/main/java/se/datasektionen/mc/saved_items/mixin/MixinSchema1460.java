package se.datasektionen.mc.saved_items.mixin;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.templates.TypeTemplate;
import net.minecraft.datafixer.TypeReferences;
import net.minecraft.datafixer.schema.Schema1460;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.datasektionen.mc.saved_items.SavedItemsDataFixer;
import se.datasektionen.mc.saved_items.item_saving.SavedItemsData;

import java.util.Map;
import java.util.function.Supplier;

@Mixin(Schema1460.class)
public class MixinSchema1460 {

	@Inject(
		method = "registerTypes",
		at = @At("RETURN")
	)
	public void registerTypes(
			Schema schema, Map<String, Supplier<TypeTemplate>> entityTypes,
			Map<String, Supplier<TypeTemplate>> blockEntityTypes, CallbackInfo ci
	) {
		schema.registerType(
			false,
			SavedItemsDataFixer.SAVED_DATA_SAVED_ITEMS,
			() -> DSL.optionalFields(
				"data", DSL.optionalFields(
						SavedItemsData.ITEMS, DSL.compoundList(
						TypeReferences.ITEM_NAME.in(schema), DSL.list(
							TypeReferences.ITEM_STACK.in(schema) //Yes, I know. This is far from ideal. But, it's not my fault that Mojang codes their datafixes to look for item stacks instead of the component list...
						)
					)
				)
			)
		);
	}

}
