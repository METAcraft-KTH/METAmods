package nu.metacraft.portable_jukebox.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.templates.TypeTemplate;
import net.minecraft.datafixer.TypeReferences;
import net.minecraft.datafixer.schema.Schema3818_3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.SequencedMap;
import java.util.function.Supplier;

@Mixin(Schema3818_3.class)
public class MixinSchema3818_3 {

	@ModifyReturnValue(
			method = "method_63573",
			at = @At("RETURN")
	)
	private static SequencedMap<String, Supplier<TypeTemplate>> registerTypes(
			SequencedMap<String, Supplier<TypeTemplate>> original, Schema schema
	) {
		original.put("portable_jukebox:portable_jukebox", () -> TypeReferences.ITEM_STACK.in(schema));
		return original;
	}

}
