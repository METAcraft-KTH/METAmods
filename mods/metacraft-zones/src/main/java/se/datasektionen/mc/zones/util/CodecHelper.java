package se.datasektionen.mc.zones.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.FieldDecoder;
import com.mojang.serialization.codecs.FieldEncoder;

public class CodecHelper {

	public static <T> MapCodec<T> fieldOfWithMigration(Codec<T> codec, String name, String secondaryName) {
		var decoder = new FieldDecoder<>(name, codec);
		((FieldDecoderData) (Object) decoder).METAcraft_Zones$setSecondaryName(secondaryName);
		return MapCodec.of(
				new FieldEncoder<>(name, codec),
				decoder,
				() -> "Field[" + name + ": " + codec.toString() + "]"
		);
	}

}
