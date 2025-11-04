package nu.metacraft.portable_jukebox.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.templates.TypeTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.SequencedMap;
import java.util.function.Supplier;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.util.datafix.schemas.V3818_3;

@Mixin(V3818_3.class)
public class MixinSchema3818_3 {

	@ModifyReturnValue(
			method = "components",
			at = @At("RETURN")
	)
	private static SequencedMap<String, Supplier<TypeTemplate>> registerTypes(
			SequencedMap<String, Supplier<TypeTemplate>> original, Schema schema
	) {
		original.put("portable_jukebox:portable_jukebox", () -> References.ITEM_STACK.in(schema));
		return original;
	}

}
