package se.metacraft.lib.mixin;

import com.mojang.serialization.Decoder;
import com.mojang.serialization.codecs.FieldDecoder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(FieldDecoder.class)
public interface FieldDecoderAccessor {

	@Accessor
	String getName();

	@Accessor
	Decoder<?> getElementCodec();

}
