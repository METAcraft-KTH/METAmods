package nu.metacraft.saved_items.mixin;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.templates.TypeTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nu.metacraft.saved_items.SavedItemsDataFixer;
import nu.metacraft.saved_items.item_saving.SavedItemsData;

import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.util.datafix.schemas.V1460;

@Mixin(V1460.class)
public class V1460Mixin {

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
						References.ITEM_NAME.in(schema), DSL.list(
							References.ITEM_STACK.in(schema) //Yes, I know. This is far from ideal. But, it's not my fault that Mojang codes their datafixes to look for item stacks instead of the component list...
						)
					)
				)
			)
		);
	}

}
