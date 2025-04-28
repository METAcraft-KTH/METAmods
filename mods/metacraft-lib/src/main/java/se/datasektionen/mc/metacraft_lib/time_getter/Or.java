package se.datasektionen.mc.metacraft_lib.time_getter;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.dynamic.Codecs;

import java.time.*;
import java.util.List;

public class Or implements RegularTimeGetter {

	public static final MapCodec<Or> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Codecs.nonEmptyList(
							Codec.lazyInitialized(() -> RegularTimeGetter.REGISTRY_CODEC).listOf()
					).fieldOf("options").forGetter(
							t -> t.options
					)
			).apply(instance, Or::new)
	);

	private final List<RegularTimeGetter> options;

	public Or(List<RegularTimeGetter> options) {
		this.options = options;
	}

	@Override
	public Instant getNextTime(Instant now) {
		return this.options.stream().map(option -> option.getNextTime(now)).sorted().findFirst().orElseThrow(
				() -> new IllegalStateException("Should always be able to get a time!")
		);
	}

	@Override
	public RegularTimeGetterType<?> getType() {
		return RegularTimeGetterRegistry.OR;
	}
}
