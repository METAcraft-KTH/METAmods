package nu.metacraft.lib.util.helper;

import com.mojang.serialization.JavaOps;
import com.mojang.serialization.MapCodec;

import java.util.stream.Stream;

public class CodecAccessHelper {

	public static Stream<String> getKnownKeys(MapCodec<?> mapCodec) {
		return mapCodec.keys(JavaOps.INSTANCE).filter(o -> o instanceof String).distinct().map(o -> (String) o);
	}

}
