package se.datasektionen.mc.portable_jukebox.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.templates.TypeTemplate;
import net.minecraft.datafixer.TypeReferences;
import net.minecraft.datafixer.schema.Schema3818;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Map;
import java.util.function.Supplier;

@Mixin(Schema3818.class)
public class MixinSchema3818 {

	@ModifyReturnValue(
		method = "registerBlockEntities",
		at = @At("RETURN")
	)
	private Map<String, Supplier<TypeTemplate>> registerBlockEntities(
			Map<String, Supplier<TypeTemplate>> original, Schema schema
	) {
		schema.register(original, "portable_jukebox:portable_jukebox", () -> DSL.optionalFields("Jukebox", TypeReferences.ITEM_STACK.in(schema)));
		return original;
	}

}
