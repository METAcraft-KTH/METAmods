package nu.metacraft.lib.mixin;

import com.mojang.serialization.Codec;
import nu.metacraft.lib.config.comments.CodecExtension;
import nu.metacraft.lib.config.comments.codecs.CodecWithComment;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Codec.class)
public interface CodecMixin<T> extends CodecExtension<T> {

	default Codec<T> metacraft$comment(String comment) {
		//noinspection unchecked
		return new CodecWithComment<>((Codec<T>) this, comment);
	}

}
