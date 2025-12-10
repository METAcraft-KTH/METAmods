package nu.metacraft.lib.time_getter;

import com.mojang.serialization.Codec;

import java.time.Instant;

public interface RegularTimeGetter {

	Codec<RegularTimeGetter> REGISTRY_CODEC = RegularTimeGetterRegistry.REGISTRY.byNameCodec().dispatch(
			RegularTimeGetter::getType, RegularTimeGetterType::codec
	);

	Instant getNextTime(Instant now);
	RegularTimeGetterType<?> getType();

}
