package nu.metacraft.lib.mixin;

import com.mojang.serialization.MapCodec;
import nu.metacraft.lib.config.comments.MapCodecExtension;
import nu.metacraft.lib.config.comments.codecs.MapCodecWithComments;
import org.spongepowered.asm.mixin.Mixin;

import java.util.Map;
import java.util.Optional;

@Mixin(MapCodec.class)
public class MapCodecMixin<T> implements MapCodecExtension<T> {

	@Override
	public MapCodec<T> metacraft$comment(String comment) {
		//noinspection unchecked
		return new MapCodecWithComments<>((MapCodec<T>) (Object) this, Map.of(), Optional.of(comment));
	}

}
