package se.datasektionen.mc.zones.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.codecs.FieldDecoder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import se.datasektionen.mc.zones.util.FieldDecoderData;

@Mixin(value = FieldDecoder.class, remap = false)
public class MixinFieldDecoder implements FieldDecoderData {

	@Unique
	private String secondaryName;

	@Override
	public void METAcraft_Zones$setSecondaryName(String secondaryName) {
		this.secondaryName = secondaryName;
	}

	@ModifyExpressionValue(
		method = "decode",
		at = @At(
			value = "INVOKE",
			target = "Lcom/mojang/serialization/MapLike;get(Ljava/lang/String;)Ljava/lang/Object;"
		)
	)
	public <T> T get(T original, @Local MapLike<T> input) {
		if (original == null) {
			return input.get(secondaryName);
		}
		return original;
	}
}
