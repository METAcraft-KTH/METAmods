package nu.metacraft.faster_minecarts.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.templates.Product;
import com.mojang.datafixers.types.templates.TypeTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import nu.metacraft.faster_minecarts.FasterMinecarts;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.util.datafix.schemas.V1460;

@Mixin(V1460.class)
public class V1460Mixin {

	@ModifyExpressionValue(
			method = {
					"lambda$registerEntities$2", //Chest minecart
					"lambda$registerEntities$3", //Command block minecart
					"lambda$registerEntities$8", //Furnace minecart
					"lambda$registerEntities$9", //Hopper minecart
					"lambda$registerEntities$14", //Minecart
					"lambda$registerEntities$18", //Spawner minecart
					"lambda$registerEntities$20" //TNT minecart
			},
			expect = 7,
			at = {
					@At(
							value = "INVOKE",
							target = "Lcom/mojang/datafixers/DSL;optionalFields(Ljava/lang/String;Lcom/mojang/datafixers/types/templates/TypeTemplate;Ljava/lang/String;Lcom/mojang/datafixers/types/templates/TypeTemplate;)Lcom/mojang/datafixers/types/templates/TypeTemplate;"
					),
					@At(
							value = "INVOKE",
							target = "Lcom/mojang/datafixers/DSL;optionalFields(Ljava/lang/String;Lcom/mojang/datafixers/types/templates/TypeTemplate;)Lcom/mojang/datafixers/types/templates/TypeTemplate;"
					),
					@At(
							value = "INVOKE",
							target = "Lcom/mojang/datafixers/DSL;optionalFields(Ljava/lang/String;Lcom/mojang/datafixers/types/templates/TypeTemplate;Lcom/mojang/datafixers/types/templates/TypeTemplate;)Lcom/mojang/datafixers/types/templates/TypeTemplate;"
					)
			}
	)
	private static TypeTemplate fixMinecarts(
			TypeTemplate original, @Local(argsOnly = true) Schema schema
	) {
		if (original instanceof Product p) {
			List<TypeTemplate> elements = new ArrayList<>();
			while (p.g() instanceof Product p2) {
				elements.add(p.f());
				p = p2;
			}
			elements.add(p.f());
			elements.add(DSL.optional(DSL.field(FasterMinecarts.MINECART_ITEM, References.ITEM_STACK.in(schema))));
			elements.add(p.g());

			return DSL.and(elements);

		} else {
			FasterMinecarts.LOGGER.error("Another mod messed up, so Minecarts won't be datafixed properly!");
			return original;
		}
	}

}
